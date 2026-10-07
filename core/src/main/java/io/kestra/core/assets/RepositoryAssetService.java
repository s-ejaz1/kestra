package io.kestra.core.assets;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import io.kestra.core.models.assets.Asset;
import io.kestra.core.models.assets.AssetIdentifier;
import io.kestra.core.models.assets.AssetLineageEdge;
import io.kestra.core.models.assets.AssetUsage;
import io.kestra.core.models.assets.AssetUser;
import io.kestra.core.models.assets.AssetsDeclaration;
import io.kestra.core.models.assets.AssetsInOut;
import io.kestra.core.models.assets.Custom;
import io.kestra.core.models.assets.External;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKind;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.AssetFailureBehavior;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.repositories.AssetLineageRepositoryInterface;
import io.kestra.core.repositories.AssetRepositoryInterface;
import io.kestra.core.repositories.AssetUsageRepositoryInterface;
import io.kestra.core.runners.RunContextFactory;

import io.micronaut.context.annotation.Requires;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Persists the assets that task runs declare or emit, the task runs that used them, and which asset was derived from which.
 */
@Slf4j
@Singleton
@Requires(beans = AssetRepositoryInterface.class)
public class RepositoryAssetService implements AssetService {
    private final AssetRepositoryInterface assetRepository;
    private final AssetUsageRepositoryInterface assetUsageRepository;
    private final AssetLineageRepositoryInterface assetLineageRepository;
    private final RunContextFactory runContextFactory;

    @Inject
    public RepositoryAssetService(
        AssetRepositoryInterface assetRepository,
        AssetUsageRepositoryInterface assetUsageRepository,
        AssetLineageRepositoryInterface assetLineageRepository,
        RunContextFactory runContextFactory) {
        this.assetRepository = assetRepository;
        this.assetUsageRepository = assetUsageRepository;
        this.assetLineageRepository = assetLineageRepository;
        this.runContextFactory = runContextFactory;
    }

    @Override
    public void asyncUpsert(AssetUser assetUser, Asset asset) {
        syncUpsert(null, assetUser, asset);
    }

    @Override
    public Asset syncUpsert(@Nullable Asset inRepository, AssetUser assetUser, Asset assetToUpsert) {
        Asset asset = assetToUpsert.withTenantId(assetUser.tenantId());
        Asset previous = Optional.ofNullable(inRepository).or(() -> assetRepository.findById(assetUser.tenantId(), asset.getId())).orElse(null);
        return assetRepository.save(asset.toUpdated(previous));
    }

    @Override
    public void assetLineage(AssetUser assetUser, List<AssetIdentifier> inputs, List<AssetIdentifier> outputs) {
        Instant now = Instant.now();
        for (AssetIdentifier input : inputs) {
            if (assetRepository.findById(assetUser.tenantId(), input.id()).isEmpty()) {
                assetRepository.save(placeholder(assetUser.tenantId(), input));
            }
            assetUsageRepository.save(usage(assetUser, input.id(), AssetUsage.Direction.INPUT, now));
        }
        for (AssetIdentifier output : outputs) {
            assetUsageRepository.save(usage(assetUser, output.id(), AssetUsage.Direction.OUTPUT, now));
            for (AssetIdentifier input : inputs) {
                assetLineageRepository.save(AssetLineageEdge.builder()
                    .tenantId(assetUser.tenantId())
                    .sourceId(input.id())
                    .targetId(output.id())
                    .namespace(assetUser.namespace())
                    .flowId(assetUser.flowId())
                    .taskId(assetUser.taskId())
                    .executionId(assetUser.executionId())
                    .date(now)
                    .build());
            }
        }
    }

    @Override
    public void deleteAsset(Asset toDelete, AssetUser assetUser) {
        assetRepository.save(toDelete.toDeleted());
    }

    @Override
    public Optional<TaskRun> processTaskRunAssets(AssetUser assetUser, TaskRun taskRun, Execution execution, Supplier<FlowWithSource> flow, ExecutionKind executionKind) {
        boolean failed = false;
        for (AssetsInOut bundle : taskRun.getAssetEmits()) {
            List<AssetIdentifier> outputs = new ArrayList<>();
            for (Asset asset : bundle.getOutputs()) {
                try {
                    outputs.add(AssetIdentifier.of(syncUpsert(null, assetUser, asset)));
                } catch (RuntimeException e) {
                    log.warn("Unable to save the asset '{}' of task run '{}'.", asset.getId(), taskRun.getId(), e);
                    failed = true;
                }
            }
            try {
                assetLineage(assetUser, bundle.getInputs(), outputs);
            } catch (RuntimeException e) {
                log.warn("Unable to save the asset lineage of task run '{}'.", taskRun.getId(), e);
                failed = true;
            }
        }
        if (!failed) {
            return Optional.empty();
        }

        FlowWithSource flowWithSource = flow.get();
        Task task = flowWithSource.findTaskByTaskIdOrNull(taskRun.getTaskId());
        if (task == null) {
            return Optional.empty();
        }
        State.Type current = taskRun.getState().getCurrent();
        State.Type escalated = clamp(task, failureBehavior(flowWithSource, task, execution, taskRun).apply(current));
        return escalated == current ? Optional.empty() : Optional.of(taskRun.withState(escalated));
    }

    private AssetFailureBehavior failureBehavior(FlowWithSource flow, Task task, Execution execution, TaskRun taskRun) {
        AssetsDeclaration assets = task.getAssets();
        if (assets == null) {
            return AssetFailureBehavior.WARN;
        }
        try {
            return runContextFactory.of(flow, task, execution, taskRun).render(assets.getAssetFailureBehavior()).as(AssetFailureBehavior.class).orElse(AssetFailureBehavior.WARN);
        } catch (Exception e) {
            log.warn("Unable to render the assetFailureBehavior of task '{}', defaulting to WARN.", task.getId(), e);
            return AssetFailureBehavior.WARN;
        }
    }

    private static State.Type clamp(Task task, State.Type state) {
        if (State.Type.FAILED == state && task.isAllowFailure()) {
            return task.isAllowWarning() ? State.Type.SUCCESS : State.Type.WARNING;
        }
        if (State.Type.WARNING == state && task.isAllowWarning()) {
            return State.Type.SUCCESS;
        }
        return state;
    }

    private static Asset placeholder(String tenantId, AssetIdentifier input) {
        if (input.type() == null || External.ASSET_TYPE.equals(input.type())) {
            return External.builder().tenantId(tenantId).namespace(input.namespace()).id(input.id()).build();
        }
        return Custom.builder().tenantId(tenantId).namespace(input.namespace()).id(input.id()).type(input.type()).build();
    }

    private static AssetUsage usage(AssetUser assetUser, String assetId, AssetUsage.Direction direction, Instant date) {
        return AssetUsage.builder()
            .tenantId(assetUser.tenantId())
            .assetId(assetId)
            .direction(direction)
            .namespace(assetUser.namespace())
            .flowId(assetUser.flowId())
            .flowRevision(assetUser.flowRevision())
            .executionId(assetUser.executionId())
            .taskId(assetUser.taskId())
            .taskRunId(assetUser.taskRunId())
            .state(assetUser.state())
            .date(date)
            .build();
    }
}

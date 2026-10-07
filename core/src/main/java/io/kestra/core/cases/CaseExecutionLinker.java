package io.kestra.core.cases;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import io.kestra.core.models.cases.Case;
import io.kestra.core.models.cases.CaseActivity;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKind;
import io.kestra.core.repositories.CaseActivityRepositoryInterface;
import io.kestra.core.repositories.CaseRepositoryInterface;
import io.kestra.core.runners.ExecutionTerminatedNotifier;
import io.kestra.core.utils.IdUtils;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Links each failed execution to the open cases of its flow that have {@link Case#isAutoLink()} set.
 */
@Singleton
@Requires(beans = CaseRepositoryInterface.class)
public class CaseExecutionLinker implements ExecutionTerminatedNotifier {
    private final CaseRepositoryInterface caseRepository;
    private final CaseActivityRepositoryInterface caseActivityRepository;

    @Inject
    public CaseExecutionLinker(CaseRepositoryInterface caseRepository, CaseActivityRepositoryInterface caseActivityRepository) {
        this.caseRepository = caseRepository;
        this.caseActivityRepository = caseActivityRepository;
    }

    @Override
    public void executionTerminated(Execution execution) {
        if (!execution.getState().isFailed() || !ExecutionKind.isNormal(execution)) {
            return;
        }
        for (Case openCase : caseRepository.findOpenAutoLinked(execution.getTenantId(), execution.getNamespace(), execution.getFlowId())) {
            if (openCase.getExecutions().stream().anyMatch(linked -> linked.executionId().equals(execution.getId()))) {
                continue;
            }
            Instant now = Instant.now();
            List<Case.LinkedExecution> executions = new ArrayList<>(openCase.getExecutions());
            executions.add(new Case.LinkedExecution(execution.getId(), execution.getNamespace(), execution.getFlowId(), execution.getState().getCurrent(), now));
            caseRepository.save(openCase.toBuilder().executions(executions).updated(now).build());
            caseActivityRepository.save(CaseActivity.builder()
                .tenantId(openCase.getTenantId())
                .id(IdUtils.create())
                .caseId(openCase.getId())
                .type(CaseActivity.Type.EXECUTION_LINKED)
                .author(CaseActivity.SYSTEM_AUTHOR)
                .date(now)
                .message(execution.getId())
                .build());
        }
    }
}

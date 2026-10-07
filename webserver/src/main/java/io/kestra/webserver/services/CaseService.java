package io.kestra.webserver.services;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.kestra.core.exceptions.ForbiddenException;
import io.kestra.core.exceptions.NotFoundException;
import io.kestra.core.exceptions.ValidationErrorException;
import io.kestra.core.models.cases.Case;
import io.kestra.core.models.cases.CaseActivity;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.iam.Action;
import io.kestra.core.models.iam.IamUser;
import io.kestra.core.models.iam.Permission;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.AssetRepositoryInterface;
import io.kestra.core.repositories.CaseActivityRepositoryInterface;
import io.kestra.core.repositories.CaseRepositoryInterface;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.IdUtils;

import io.micronaut.data.model.Pageable;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Opens, triages and resolves cases, recording every change in the case's timeline, and checks the {@link Permission#CASE} permission
 * on the case's namespace. A case without a namespace is visible to anyone with CASE access, and only instance-wide grants can change it.
 */
@Singleton
public class CaseService {
    private static final Set<Action> READ_ACTIONS = Set.of(Action.VIEW, Action.LIST);

    private final CaseRepositoryInterface caseRepository;
    private final CaseActivityRepositoryInterface caseActivityRepository;
    private final ExecutionRepositoryInterface executionRepository;
    private final FlowRepositoryInterface flowRepository;
    private final AssetRepositoryInterface assetRepository;
    private final IamService iamService;
    private final TenantService tenantService;

    @Inject
    public CaseService(
        CaseRepositoryInterface caseRepository,
        CaseActivityRepositoryInterface caseActivityRepository,
        ExecutionRepositoryInterface executionRepository,
        FlowRepositoryInterface flowRepository,
        AssetRepositoryInterface assetRepository,
        IamService iamService,
        TenantService tenantService) {
        this.caseRepository = caseRepository;
        this.caseActivityRepository = caseActivityRepository;
        this.executionRepository = executionRepository;
        this.flowRepository = flowRepository;
        this.assetRepository = assetRepository;
        this.iamService = iamService;
        this.tenantService = tenantService;
    }

    public ArrayListTotal<Case> search(
        UserGrants grants,
        Pageable pageable,
        @Nullable String query,
        @Nullable List<Case.Status> statuses,
        @Nullable Case.Severity severity,
        @Nullable String namespace) {
        return caseRepository.find(pageable, tenant(), grants.scope(Permission.CASE, READ_ACTIONS), query, statuses, severity, namespace);
    }

    public Case get(UserGrants grants, String id) {
        Case found = find(id);
        require(grants, READ_ACTIONS, found.getNamespace());
        return found;
    }

    public ArrayListTotal<CaseActivity> activities(UserGrants grants, Pageable pageable, String id) {
        get(grants, id);
        return caseActivityRepository.find(pageable, tenant(), id);
    }

    public List<IamUser> assignees() {
        return iamService.listUsers().stream().filter(user -> !user.isDisabled()).toList();
    }

    public Case create(UserGrants grants, String author, Draft draft, List<String> executionIds) {
        require(grants, Set.of(Action.CREATE), draft.namespace());
        validate(draft);
        Instant now = Instant.now();
        List<Case.LinkedExecution> executions = executionIds.stream().distinct().map(executionId -> linked(executionId, now)).toList();
        Case created = caseRepository.save(Case.builder()
            .tenantId(tenant())
            .id(IdUtils.create())
            .title(draft.title())
            .description(draft.description())
            .status(Case.Status.OPEN)
            .severity(draft.severity())
            .namespace(draft.namespace())
            .flowId(draft.flowId())
            .autoLink(draft.autoLink())
            .assignees(draft.assignees())
            .labels(draft.labels())
            .executions(executions)
            .dueDate(Optional.ofNullable(draft.dueDate()).orElse(now.plus(draft.severity().sla)))
            .createdBy(author)
            .created(now)
            .updated(now)
            .build());
        record(created, CaseActivity.Type.CREATED, author, null, null, null);
        executions.forEach(execution -> record(created, CaseActivity.Type.EXECUTION_LINKED, author, execution.executionId(), null, null));
        return created;
    }

    public Case update(UserGrants grants, String author, String id, Draft draft) {
        Case existing = find(id);
        require(grants, Set.of(Action.UPDATE), existing.getNamespace());
        require(grants, Set.of(Action.UPDATE), draft.namespace());
        validate(draft);

        Case updated = caseRepository.save(existing.toBuilder()
            .title(draft.title())
            .description(draft.description())
            .severity(draft.severity())
            .namespace(draft.namespace())
            .flowId(draft.flowId())
            .autoLink(draft.autoLink())
            .assignees(draft.assignees())
            .labels(draft.labels())
            .dueDate(draft.dueDate())
            .updated(Instant.now())
            .build());

        if (existing.getSeverity() != updated.getSeverity()) {
            record(updated, CaseActivity.Type.SEVERITY_CHANGED, author, null, existing.getSeverity().name(), updated.getSeverity().name());
        }
        if (!existing.getAssignees().equals(updated.getAssignees())) {
            record(updated, CaseActivity.Type.ASSIGNEES_CHANGED, author, null, String.join(", ", existing.getAssignees()), String.join(", ", updated.getAssignees()));
        }
        if (!Objects.equals(existing.getDueDate(), updated.getDueDate())) {
            record(updated, CaseActivity.Type.DUE_DATE_CHANGED, author, null, Objects.toString(existing.getDueDate(), null), Objects.toString(updated.getDueDate(), null));
        }
        if (!Objects.equals(existing.getTitle(), updated.getTitle())
            || !Objects.equals(existing.getDescription(), updated.getDescription())
            || !Objects.equals(existing.getNamespace(), updated.getNamespace())
            || !Objects.equals(existing.getFlowId(), updated.getFlowId())
            || existing.isAutoLink() != updated.isAutoLink()
            || !existing.getLabels().equals(updated.getLabels())) {
            record(updated, CaseActivity.Type.UPDATED, author, null, null, null);
        }
        return updated;
    }

    public Case changeStatus(UserGrants grants, String author, String id, Case.Status status, @Nullable String comment) {
        Case existing = find(id);
        require(grants, Set.of(Action.UPDATE), existing.getNamespace());
        if (Case.Status.UNKNOWN == status) {
            throw new ValidationErrorException(List.of("The status must be OPEN, ACKNOWLEDGED, RESOLVED or CLOSED."));
        }
        if (existing.getStatus() == status) {
            return existing;
        }

        Instant now = Instant.now();
        boolean closing = Case.Status.RESOLVED == status || Case.Status.CLOSED == status;
        Case changed = caseRepository.save(existing.toBuilder()
            .status(status)
            .acknowledgedDate(Case.Status.ACKNOWLEDGED == status && existing.getAcknowledgedDate() == null ? now : existing.getAcknowledgedDate())
            .resolvedDate(closing ? Optional.ofNullable(existing.getResolvedDate()).orElse(now) : null)
            .updated(now)
            .build());
        record(changed, CaseActivity.Type.STATUS_CHANGED, author, comment, existing.getStatus().name(), status.name());
        return changed;
    }

    public CaseActivity comment(UserGrants grants, String author, String id, String message) {
        Case existing = find(id);
        require(grants, Set.of(Action.UPDATE), existing.getNamespace());
        if (message == null || message.isBlank()) {
            throw new ValidationErrorException(List.of("A comment cannot be empty."));
        }
        caseRepository.save(existing.toBuilder().updated(Instant.now()).build());
        return record(existing, CaseActivity.Type.COMMENT, author, message, null, null);
    }

    public Case linkExecution(UserGrants grants, String author, String id, String executionId) {
        Case existing = find(id);
        require(grants, Set.of(Action.UPDATE), existing.getNamespace());
        if (existing.getExecutions().stream().anyMatch(linked -> linked.executionId().equals(executionId))) {
            return existing;
        }
        Instant now = Instant.now();
        List<Case.LinkedExecution> executions = new ArrayList<>(existing.getExecutions());
        executions.add(linked(executionId, now));
        Case changed = caseRepository.save(existing.toBuilder().executions(executions).updated(now).build());
        record(changed, CaseActivity.Type.EXECUTION_LINKED, author, executionId, null, null);
        return changed;
    }

    public Case unlinkExecution(UserGrants grants, String author, String id, String executionId) {
        Case existing = find(id);
        require(grants, Set.of(Action.UPDATE), existing.getNamespace());
        List<Case.LinkedExecution> executions = existing.getExecutions().stream().filter(linked -> !linked.executionId().equals(executionId)).toList();
        if (executions.size() == existing.getExecutions().size()) {
            return existing;
        }
        Case changed = caseRepository.save(existing.toBuilder().executions(executions).updated(Instant.now()).build());
        record(changed, CaseActivity.Type.EXECUTION_UNLINKED, author, executionId, null, null);
        return changed;
    }

    public Case linkAsset(UserGrants grants, String author, String id, String assetId) {
        Case existing = find(id);
        require(grants, Set.of(Action.UPDATE), existing.getNamespace());
        if (existing.getAssets().contains(assetId)) {
            return existing;
        }
        if (assetRepository.findById(tenant(), assetId).isEmpty()) {
            throw new NotFoundException("The asset '%s' does not exist.".formatted(assetId));
        }
        List<String> assets = new ArrayList<>(existing.getAssets());
        assets.add(assetId);
        Case changed = caseRepository.save(existing.toBuilder().assets(assets).updated(Instant.now()).build());
        record(changed, CaseActivity.Type.ASSET_LINKED, author, assetId, null, null);
        return changed;
    }

    public Case unlinkAsset(UserGrants grants, String author, String id, String assetId) {
        Case existing = find(id);
        require(grants, Set.of(Action.UPDATE), existing.getNamespace());
        if (!existing.getAssets().contains(assetId)) {
            return existing;
        }
        List<String> assets = existing.getAssets().stream().filter(asset -> !asset.equals(assetId)).toList();
        Case changed = caseRepository.save(existing.toBuilder().assets(assets).updated(Instant.now()).build());
        record(changed, CaseActivity.Type.ASSET_UNLINKED, author, assetId, null, null);
        return changed;
    }

    public void delete(UserGrants grants, String id) {
        Case existing = find(id);
        require(grants, Set.of(Action.DELETE), existing.getNamespace());
        caseRepository.save(existing.toDeleted());
    }

    private void validate(Draft draft) {
        List<String> errors = new ArrayList<>();
        if (draft.title() == null || draft.title().isBlank()) {
            errors.add("A case needs a title.");
        }
        if (Case.Severity.UNKNOWN == draft.severity()) {
            errors.add("The severity must be CRITICAL, HIGH, MEDIUM or LOW.");
        }
        if (draft.flowId() != null && draft.namespace() == null) {
            errors.add("A case about a flow needs the namespace of that flow.");
        } else if (draft.flowId() != null && flowRepository.findById(tenant(), draft.namespace(), draft.flowId()).isEmpty()) {
            errors.add("The flow '%s' does not exist in namespace '%s'.".formatted(draft.flowId(), draft.namespace()));
        }
        if (draft.autoLink() && draft.flowId() == null) {
            errors.add("Linking failed executions automatically needs the flow they come from.");
        }
        if (!errors.isEmpty()) {
            throw new ValidationErrorException(errors);
        }
    }

    private Case.LinkedExecution linked(String executionId, Instant now) {
        Execution execution = executionRepository.findById(tenant(), executionId)
            .orElseThrow(() -> new NotFoundException("The execution '%s' does not exist.".formatted(executionId)));
        return new Case.LinkedExecution(execution.getId(), execution.getNamespace(), execution.getFlowId(), execution.getState().getCurrent(), now);
    }

    private CaseActivity record(Case target, CaseActivity.Type type, String author, @Nullable String message, @Nullable String from, @Nullable String to) {
        return caseActivityRepository.save(CaseActivity.builder()
            .tenantId(target.getTenantId())
            .id(IdUtils.create())
            .caseId(target.getId())
            .type(type)
            .author(author)
            .date(Instant.now())
            .message(message)
            .from(from)
            .to(to)
            .build());
    }

    private void require(UserGrants grants, Set<Action> actions, @Nullable String namespace) {
        boolean allowed = namespace == null && !READ_ACTIONS.containsAll(actions)
            ? grants.allowsOnInstance(Permission.CASE, actions)
            : grants.allows(Permission.CASE, actions, namespace);
        if (!allowed) {
            throw new ForbiddenException("The user needs one of the actions %s on CASE%s.".formatted(
                actions.stream().map(Action::name).sorted().toList(),
                namespace == null ? " on the whole instance" : " in namespace '%s'".formatted(namespace)
            ));
        }
    }

    private Case find(String id) {
        return caseRepository.findById(tenant(), id)
            .orElseThrow(() -> new NotFoundException("The case '%s' does not exist.".formatted(id)));
    }

    private String tenant() {
        return tenantService.resolveTenant();
    }

    /**
     * The fields of a case its users edit directly; the status, the links and the timeline change through their own operations.
     */
    public record Draft(
        String title,
        @Nullable String description,
        Case.Severity severity,
        @Nullable String namespace,
        @Nullable String flowId,
        boolean autoLink,
        List<String> assignees,
        List<String> labels,
        @Nullable Instant dueDate) {
    }
}

package io.kestra.webserver.services;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.kestra.core.exceptions.FlowProcessingException;
import io.kestra.core.exceptions.NotFoundException;
import io.kestra.core.exceptions.ValidationErrorException;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.policies.Policy;
import io.kestra.core.policies.PolicyEngine;
import io.kestra.core.policies.PolicyFlowParsingService;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.PolicyRepositoryInterface;
import io.kestra.core.runners.FlowWithDefaultCache;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.tenant.TenantService;

import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Stores policies declared in YAML and shows what they do to a flow. Changing a policy expires the flows processed for runtime,
 * so that the next executions run with it.
 */
@Singleton
public class PolicyService {
    private static final Pattern ID_PATTERN = Pattern.compile("[a-zA-Z0-9][a-zA-Z0-9._-]*");

    private final PolicyRepositoryInterface policyRepository;
    private final FlowRepositoryInterface flowRepository;
    private final PolicyFlowParsingService policyFlowParsingService;
    private final FlowWithDefaultCache flowWithDefaultCache;
    private final TenantService tenantService;

    @Inject
    public PolicyService(
        PolicyRepositoryInterface policyRepository,
        FlowRepositoryInterface flowRepository,
        PolicyFlowParsingService policyFlowParsingService,
        FlowWithDefaultCache flowWithDefaultCache,
        TenantService tenantService) {
        this.policyRepository = policyRepository;
        this.flowRepository = flowRepository;
        this.policyFlowParsingService = policyFlowParsingService;
        this.flowWithDefaultCache = flowWithDefaultCache;
        this.tenantService = tenantService;
    }

    public List<Policy> list(@Nullable String namespace) {
        return policyRepository.findAll(tenant()).stream()
            .filter(policy -> namespace == null || policy.appliesToNamespace(namespace))
            .toList();
    }

    public Policy get(String id) {
        return policyRepository.findById(tenant(), id)
            .orElseThrow(() -> new NotFoundException("The policy '%s' does not exist.".formatted(id)));
    }

    public Policy create(String source) {
        Policy policy = parse(source);
        if (policyRepository.findById(tenant(), policy.getId()).isPresent()) {
            throw new ValidationErrorException(List.of("A policy '%s' already exists.".formatted(policy.getId())));
        }
        return saved(policyRepository.save(policy));
    }

    public Policy update(String id, String source) {
        Policy existing = get(id);
        Policy policy = parse(source);
        if (!id.equals(policy.getId())) {
            throw new ValidationErrorException(List.of("The id of a policy cannot be changed; create a new policy instead."));
        }
        return saved(policyRepository.save(policy.toBuilder().created(existing.getCreated()).build()));
    }

    public void delete(String id) {
        saved(policyRepository.save(get(id).toDeleted()));
    }

    /**
     * What the policies in scope do to a stored flow: the flow they produce, the values they inject and the violations they find.
     */
    public Preview preview(String namespace, String flowId) {
        FlowWithSource flow = flowRepository.findByIdWithSource(tenant(), namespace, flowId)
            .orElseThrow(() -> new NotFoundException("The flow '%s' does not exist in namespace '%s'.".formatted(flowId, namespace)));
        try {
            PolicyFlowParsingService.Applied applied = policyFlowParsingService.applyPolicies(flow);
            if (applied == null) {
                return new Preview(flow.getSource(), List.of(), List.of(), List.of());
            }
            PolicyEngine.Result result = applied.result();
            String effective = JacksonMapper.ofYaml().writeValueAsString(result.flow());
            return new Preview(effective, result.notices(), result.blocking(), result.audits());
        } catch (FlowProcessingException | JsonProcessingException e) {
            throw new ValidationErrorException(List.of("The policies could not be applied to the flow: %s".formatted(e.getMessage())));
        }
    }

    private Policy saved(Policy policy) {
        flowWithDefaultCache.flush(tenant());
        return policy;
    }

    private Policy parse(String source) {
        Policy policy;
        try {
            policy = JacksonMapper.ofYaml().readValue(source, Policy.class);
        } catch (JsonProcessingException e) {
            throw new ValidationErrorException(List.of("The policy is not valid YAML: %s".formatted(e.getOriginalMessage())));
        }
        if (policy == null) {
            throw new ValidationErrorException(List.of("The policy definition is empty."));
        }

        List<String> errors = new ArrayList<>();
        if (policy.getId() == null || !ID_PATTERN.matcher(policy.getId()).matches()) {
            errors.add("The policy id is required and may only contain letters, digits, '.', '_' and '-'.");
        }
        if (policy.getEnforcement() == null || Policy.Enforcement.UNKNOWN == policy.getEnforcement()) {
            errors.add("The enforcement must be ENFORCE, AUDIT, REFERENCE or DISABLED.");
        }
        if (policy.getRules() == null || policy.getRules().isEmpty()) {
            errors.add("A policy needs at least one rule.");
        }
        if (!errors.isEmpty()) {
            throw new ValidationErrorException(errors);
        }
        return policy.toBuilder().tenantId(tenant()).source(source).build();
    }

    private String tenant() {
        return tenantService.resolveTenant();
    }

    /**
     * @param source the flow as it runs, with the policies applied
     */
    public record Preview(String source, List<String> changes, List<String> blocking, List<String> audits) {
    }
}

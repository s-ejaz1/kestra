package io.kestra.core.policies;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.kestra.core.exceptions.FlowBlockedException;
import io.kestra.core.exceptions.FlowProcessingException;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.policies.Policy;
import io.kestra.core.repositories.PolicyRepositoryInterface;
import io.kestra.core.runners.ProcessedFlow;
import io.kestra.core.serializers.YamlParser;
import io.kestra.core.services.FlowParsingService;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.ConstraintViolationException;

/**
 * Applies the policies in scope of a flow's namespace whenever it is parsed to run or to be validated: their defaults and labels are
 * injected, and a flow violating an enforced policy is blocked from running.
 */
@Singleton
@Replaces(FlowParsingService.class)
@Requires(beans = PolicyRepositoryInterface.class)
public class PolicyFlowParsingService extends FlowParsingService {
    private final PolicyRepositoryInterface policyRepository;

    @Inject
    public PolicyFlowParsingService(PolicyRepositoryInterface policyRepository) {
        this.policyRepository = policyRepository;
    }

    @Override
    public ProcessedFlow parseForRuntime(final FlowInterface flow) throws FlowProcessingException {
        Applied applied = applyPolicies(flow);
        if (applied == null) {
            return super.parseForRuntime(flow);
        }
        List<String> blocking = applied.result().blocking();
        if (!blocking.isEmpty()) {
            throw new FlowBlockedException("The flow is blocked by governance policies: %s".formatted(String.join(" ", blocking)));
        }
        return new ProcessedFlow(applied.flow(), applied.result().pinnedLabelKeys());
    }

    @Override
    public FlowWithSource parseForValidation(final FlowInterface flow) throws FlowProcessingException {
        Applied applied = applyPolicies(flow);
        return applied == null ? super.parseForValidation(flow) : applied.flow();
    }

    @Override
    public GovernanceReport governance(final FlowInterface flow) throws FlowProcessingException {
        Applied applied = applyPolicies(flow);
        if (applied == null) {
            return GovernanceReport.EMPTY;
        }
        return new GovernanceReport(applied.result().blocking(), applied.result().audits(), applied.result().notices());
    }

    /**
     * Applies the policies to the flow as written; {@code null} when no policy is in scope of its namespace.
     */
    @Nullable
    public Applied applyPolicies(final FlowInterface flow) throws FlowProcessingException {
        List<Policy> policies = policyRepository.findAll(flow.getTenantId()).stream()
            .filter(policy -> Policy.Enforcement.DISABLED != policy.getEnforcement() && Policy.Enforcement.UNKNOWN != policy.getEnforcement())
            .filter(policy -> flow.getNamespace() != null && policy.appliesToNamespace(flow.getNamespace()))
            .toList();
        if (policies.isEmpty()) {
            return null;
        }

        String source = flow.sourceOrGenerateIfNull();
        try {
            Map<String, Object> map = readFlowAsMap(flow.getTenantId(), flow.getNamespace(), source);
            PolicyEngine.Result result = PolicyEngine.apply(map, policies);
            FlowWithSource parsed = parseFlowFromMap(result.flow(), flow.getTenantId(), flow.getRevision(), flow.isDeleted(), source, false);
            return new Applied(parsed, result);
        } catch (ConstraintViolationException e) {
            throw new FlowProcessingException(e);
        } catch (JsonProcessingException e) {
            throw new FlowProcessingException(YamlParser.toConstraintViolationException(source, "Flow", e));
        }
    }

    /**
     * @param flow the flow parsed with the policies applied, its source left as written
     */
    public record Applied(FlowWithSource flow, PolicyEngine.Result result) {
    }
}

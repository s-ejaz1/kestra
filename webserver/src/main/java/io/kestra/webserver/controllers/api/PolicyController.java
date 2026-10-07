package io.kestra.webserver.controllers.api;

import java.util.List;

import io.kestra.core.http.KestraMediaTypes;
import io.kestra.core.models.policies.Policy;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.webserver.responses.PagedResults;
import io.kestra.webserver.services.PolicyService;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

/**
 * Governance policies; not mapped to a permission, so only administrators can use it.
 */
@Controller("/api/v1/{tenant}/policies")
public class PolicyController {
    private final PolicyService policyService;

    @Inject
    public PolicyController(PolicyService policyService) {
        this.policyService = policyService;
    }

    @Get
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Policies" }, summary = "List the policies, or the ones in scope of a namespace")
    public PagedResults<Policy> listPolicies(@Parameter(description = "A namespace, to keep the policies applying to it") @Nullable @QueryValue String namespace) {
        List<Policy> policies = policyService.list(namespace);
        return PagedResults.of(new ArrayListTotal<>(policies, policies.size()));
    }

    @Get("/{id}")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Policies" }, summary = "Retrieve a policy")
    public Policy getPolicy(@Parameter(description = "The policy id") @PathVariable String id) {
        return policyService.get(id);
    }

    @Post(consumes = { KestraMediaTypes.APPLICATION_X_YAML, MediaType.APPLICATION_YAML, MediaType.TEXT_PLAIN })
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Policies" }, summary = "Create a policy from its YAML definition")
    public Policy createPolicy(@RequestBody(description = "The policy YAML") @Body String source) {
        return policyService.create(source);
    }

    @Put(uri = "/{id}", consumes = { KestraMediaTypes.APPLICATION_X_YAML, MediaType.APPLICATION_YAML, MediaType.TEXT_PLAIN })
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Policies" }, summary = "Replace a policy's YAML definition")
    public Policy updatePolicy(@Parameter(description = "The policy id") @PathVariable String id, @RequestBody(description = "The policy YAML") @Body String source) {
        return policyService.update(id, source);
    }

    @Delete("/{id}")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Policies" }, summary = "Delete a policy")
    public HttpResponse<Void> deletePolicy(@Parameter(description = "The policy id") @PathVariable String id) {
        policyService.delete(id);
        return HttpResponse.noContent();
    }

    @Get("/preview/{namespace}/{flowId}")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Policies" }, summary = "Show what the policies in scope do to a flow")
    public PolicyService.Preview previewPolicies(
        @Parameter(description = "The flow namespace") @PathVariable String namespace,
        @Parameter(description = "The flow id") @PathVariable String flowId) {
        return policyService.preview(namespace, flowId);
    }
}

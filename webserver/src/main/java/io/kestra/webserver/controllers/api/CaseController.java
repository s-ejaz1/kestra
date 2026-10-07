package io.kestra.webserver.controllers.api;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import io.kestra.core.exceptions.ForbiddenException;
import io.kestra.core.models.cases.Case;
import io.kestra.core.models.cases.CaseActivity;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.webserver.responses.PagedResults;
import io.kestra.webserver.services.AuthenticatedUser;
import io.kestra.webserver.services.CaseService;
import io.kestra.webserver.services.IamService;
import io.kestra.webserver.services.UserGrants;
import io.kestra.webserver.utils.PageableUtils;

import io.micronaut.core.convert.format.Format;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
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
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@Controller("/api/v1/{tenant}/cases")
public class CaseController {
    private final CaseService caseService;
    private final IamService iamService;

    @Inject
    public CaseController(CaseService caseService, IamService iamService) {
        this.caseService = caseService;
        this.iamService = iamService;
    }

    @Get("/search")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Cases" }, summary = "Search the cases the caller can see, most recently updated first")
    public PagedResults<Case> searchCases(
        HttpRequest<?> request,
        @Parameter(description = "The current page") @QueryValue(defaultValue = "1") @Min(1) int page,
        @Parameter(description = "The current page size") @QueryValue(defaultValue = "25") @Min(1) int size,
        @Parameter(description = "A text matched against the title, or a case id") @Nullable @QueryValue String q,
        @Parameter(description = "The statuses to keep") @Nullable @QueryValue @Format("MULTI") List<Case.Status> status,
        @Parameter(description = "The severity to keep") @Nullable @QueryValue Case.Severity severity,
        @Parameter(description = "A namespace, matching its children too") @Nullable @QueryValue String namespace) {
        return PagedResults.of(caseService.search(grants(request), PageableUtils.from(page, size), q, status, severity, namespace));
    }

    @Get("/assignees")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Cases" }, summary = "List the users a case can be assigned to")
    public PagedResults<Assignee> listAssignees(HttpRequest<?> request) {
        grants(request);
        List<Assignee> assignees = caseService.assignees().stream()
            .map(user -> new Assignee(user.getEmail(), user.getFirstName(), user.getLastName()))
            .toList();
        return PagedResults.of(new ArrayListTotal<>(assignees, assignees.size()));
    }

    @Get("/{id}")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Cases" }, summary = "Retrieve a case")
    public Case getCase(HttpRequest<?> request, @Parameter(description = "The case id") @PathVariable String id) {
        return caseService.get(grants(request), id);
    }

    @Get("/{id}/activities")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Cases" }, summary = "List the timeline of a case, oldest first")
    public PagedResults<CaseActivity> listCaseActivities(
        HttpRequest<?> request,
        @Parameter(description = "The case id") @PathVariable String id,
        @Parameter(description = "The current page") @QueryValue(defaultValue = "1") @Min(1) int page,
        @Parameter(description = "The current page size") @QueryValue(defaultValue = "100") @Min(1) int size) {
        return PagedResults.of(caseService.activities(grants(request), PageableUtils.from(page, size), id));
    }

    @Post
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Cases" }, summary = "Open a case")
    public Case createCase(HttpRequest<?> request, @Body @Valid CaseRequest body) {
        return caseService.create(grants(request), author(request), body.toDraft(), Optional.ofNullable(body.executionIds()).orElse(List.of()));
    }

    @Put("/{id}")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Cases" }, summary = "Edit a case")
    public Case updateCase(HttpRequest<?> request, @Parameter(description = "The case id") @PathVariable String id, @Body @Valid CaseRequest body) {
        return caseService.update(grants(request), author(request), id, body.toDraft());
    }

    @Post("/{id}/status")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Cases" }, summary = "Acknowledge, resolve, close or reopen a case")
    public Case changeCaseStatus(HttpRequest<?> request, @Parameter(description = "The case id") @PathVariable String id, @Body @Valid StatusRequest body) {
        return caseService.changeStatus(grants(request), author(request), id, body.status(), body.comment());
    }

    @Post("/{id}/comments")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Cases" }, summary = "Comment on a case")
    public CaseActivity commentCase(HttpRequest<?> request, @Parameter(description = "The case id") @PathVariable String id, @Body @Valid CommentRequest body) {
        return caseService.comment(grants(request), author(request), id, body.message());
    }

    @Post("/{id}/executions")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Cases" }, summary = "Link an execution to a case")
    public Case linkCaseExecution(HttpRequest<?> request, @Parameter(description = "The case id") @PathVariable String id, @Body @Valid ExecutionLinkRequest body) {
        return caseService.linkExecution(grants(request), author(request), id, body.executionId());
    }

    @Delete("/{id}/executions/{executionId}")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Cases" }, summary = "Unlink an execution from a case")
    public Case unlinkCaseExecution(
        HttpRequest<?> request,
        @Parameter(description = "The case id") @PathVariable String id,
        @Parameter(description = "The execution id") @PathVariable String executionId) {
        return caseService.unlinkExecution(grants(request), author(request), id, executionId);
    }

    @Post("/{id}/assets")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Cases" }, summary = "Link an asset to a case")
    public Case linkCaseAsset(HttpRequest<?> request, @Parameter(description = "The case id") @PathVariable String id, @Body @Valid AssetLinkRequest body) {
        return caseService.linkAsset(grants(request), author(request), id, body.assetId());
    }

    @Delete("/{id}/assets/{assetId}")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Cases" }, summary = "Unlink an asset from a case")
    public Case unlinkCaseAsset(
        HttpRequest<?> request,
        @Parameter(description = "The case id") @PathVariable String id,
        @Parameter(description = "The asset id") @PathVariable String assetId) {
        return caseService.unlinkAsset(grants(request), author(request), id, assetId);
    }

    @Delete("/{id}")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Cases" }, summary = "Delete a case")
    public HttpResponse<Void> deleteCase(HttpRequest<?> request, @Parameter(description = "The case id") @PathVariable String id) {
        caseService.delete(grants(request), id);
        return HttpResponse.noContent();
    }

    private UserGrants grants(HttpRequest<?> request) {
        return UserGrants.of(request, user(request), iamService);
    }

    private static String author(HttpRequest<?> request) {
        return user(request).email();
    }

    private static AuthenticatedUser user(HttpRequest<?> request) {
        return AuthenticatedUser.from(request).orElseThrow(() -> new ForbiddenException("The request is not authenticated."));
    }

    public record CaseRequest(
        @NotBlank String title,
        @Nullable String description,
        @Nullable Case.Severity severity,
        @Nullable String namespace,
        @Nullable String flowId,
        @Nullable Boolean autoLink,
        @Nullable List<String> assignees,
        @Nullable List<String> labels,
        @Nullable Instant dueDate,
        @Nullable List<String> executionIds) {

        CaseService.Draft toDraft() {
            return new CaseService.Draft(
                title,
                description,
                Optional.ofNullable(severity).orElse(Case.Severity.MEDIUM),
                blankToNull(namespace),
                blankToNull(flowId),
                Boolean.TRUE.equals(autoLink),
                Optional.ofNullable(assignees).orElse(List.of()),
                Optional.ofNullable(labels).orElse(List.of()),
                dueDate
            );
        }

        @Nullable
        private static String blankToNull(@Nullable String value) {
            return value == null || value.isBlank() ? null : value;
        }
    }

    public record StatusRequest(Case.Status status, @Nullable String comment) {
    }

    public record CommentRequest(@NotBlank String message) {
    }

    public record ExecutionLinkRequest(@NotBlank String executionId) {
    }

    public record AssetLinkRequest(@NotBlank String assetId) {
    }

    public record Assignee(String email, @Nullable String firstName, @Nullable String lastName) {
    }
}

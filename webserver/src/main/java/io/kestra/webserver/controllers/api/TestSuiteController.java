package io.kestra.webserver.controllers.api;

import java.util.List;

import io.kestra.core.exceptions.ForbiddenException;
import io.kestra.core.http.KestraMediaTypes;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.test.TestSuite;
import io.kestra.core.test.TestSuiteRunResult;
import io.kestra.webserver.responses.PagedResults;
import io.kestra.webserver.services.AuthenticatedUser;
import io.kestra.webserver.services.IamService;
import io.kestra.webserver.services.TestSuiteService;
import io.kestra.webserver.services.UserGrants;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

@Controller("/api/v1/{tenant}/tests")
public class TestSuiteController {
    private final TestSuiteService testSuiteService;
    private final IamService iamService;

    @Inject
    public TestSuiteController(TestSuiteService testSuiteService, IamService iamService) {
        this.testSuiteService = testSuiteService;
        this.iamService = iamService;
    }

    @Get
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Tests" }, summary = "List the test suites the caller can see, with their last run")
    public PagedResults<TestSuiteWithLastRun> listTestSuites(HttpRequest<?> request) {
        List<TestSuiteWithLastRun> testSuites = testSuiteService.list(grants(request)).stream().map(this::withLastRun).toList();
        return PagedResults.of(new ArrayListTotal<>(testSuites, testSuites.size()));
    }

    @Get("/{namespace}/{id}")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Tests" }, summary = "Retrieve a test suite with its last run")
    public TestSuiteWithLastRun getTestSuite(
        HttpRequest<?> request,
        @Parameter(description = "The test suite namespace") @PathVariable String namespace,
        @Parameter(description = "The test suite id") @PathVariable String id) {
        return withLastRun(testSuiteService.get(grants(request), namespace, id));
    }

    @Post(consumes = { KestraMediaTypes.APPLICATION_X_YAML, MediaType.APPLICATION_YAML, MediaType.TEXT_PLAIN })
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Tests" }, summary = "Create a test suite from its YAML definition")
    public TestSuite createTestSuite(HttpRequest<?> request, @RequestBody(description = "The test suite YAML") @Body String source) {
        return testSuiteService.create(grants(request), source);
    }

    @Put(uri = "/{namespace}/{id}", consumes = { KestraMediaTypes.APPLICATION_X_YAML, MediaType.APPLICATION_YAML, MediaType.TEXT_PLAIN })
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Tests" }, summary = "Replace a test suite's YAML definition")
    public TestSuite updateTestSuite(
        HttpRequest<?> request,
        @Parameter(description = "The test suite namespace") @PathVariable String namespace,
        @Parameter(description = "The test suite id") @PathVariable String id,
        @RequestBody(description = "The test suite YAML") @Body String source) {
        return testSuiteService.update(grants(request), namespace, id, source);
    }

    @Delete("/{namespace}/{id}")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Tests" }, summary = "Delete a test suite")
    public HttpResponse<Void> deleteTestSuite(
        HttpRequest<?> request,
        @Parameter(description = "The test suite namespace") @PathVariable String namespace,
        @Parameter(description = "The test suite id") @PathVariable String id) {
        testSuiteService.delete(grants(request), namespace, id);
        return HttpResponse.noContent();
    }

    @Post("/{namespace}/{id}/run")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Tests" }, summary = "Run every test case of a test suite and wait for the results")
    public TestSuiteRunResult runTestSuite(
        HttpRequest<?> request,
        @Parameter(description = "The test suite namespace") @PathVariable String namespace,
        @Parameter(description = "The test suite id") @PathVariable String id) {
        return testSuiteService.run(grants(request), namespace, id);
    }

    private TestSuiteWithLastRun withLastRun(TestSuite testSuite) {
        return new TestSuiteWithLastRun(testSuite, testSuiteService.lastRun(testSuite.getNamespace(), testSuite.getId()).orElse(null));
    }

    private UserGrants grants(HttpRequest<?> request) {
        AuthenticatedUser user = AuthenticatedUser.from(request)
            .orElseThrow(() -> new ForbiddenException("The request is not authenticated."));
        return UserGrants.of(request, user, iamService);
    }

    public record TestSuiteWithLastRun(TestSuite testSuite, @Nullable TestSuiteRunResult lastRun) {
    }
}

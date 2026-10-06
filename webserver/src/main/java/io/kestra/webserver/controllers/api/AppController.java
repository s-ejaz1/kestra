package io.kestra.webserver.controllers.api;

import java.util.List;

import io.kestra.core.exceptions.ForbiddenException;
import io.kestra.core.models.apps.App;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.webserver.responses.PagedResults;
import io.kestra.webserver.services.AppService;
import io.kestra.webserver.services.AuthenticatedUser;
import io.kestra.webserver.services.IamService;
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
import jakarta.inject.Inject;

@Controller("/api/v1/{tenant}/apps")
public class AppController {
    private final AppService appService;
    private final IamService iamService;

    @Inject
    public AppController(AppService appService, IamService iamService) {
        this.appService = appService;
        this.iamService = iamService;
    }

    @Get
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Apps" }, summary = "List the apps the caller can open")
    public PagedResults<App> listApps(HttpRequest<?> request) {
        List<App> apps = appService.list(grants(request));
        return PagedResults.of(new ArrayListTotal<>(apps, apps.size()));
    }

    @Get("/{namespace}/{id}")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Apps" }, summary = "Retrieve an app")
    public App getApp(
        HttpRequest<?> request,
        @Parameter(description = "The app namespace") @PathVariable String namespace,
        @Parameter(description = "The app id") @PathVariable String id) {
        return appService.get(grants(request), namespace, id);
    }

    @Post(consumes = { MediaType.APPLICATION_YAML, MediaType.TEXT_PLAIN })
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Apps" }, summary = "Create an app from its YAML definition")
    public App createApp(HttpRequest<?> request, @RequestBody(description = "The app YAML") @Body String source) {
        return appService.create(grants(request), source);
    }

    @Put(uri = "/{namespace}/{id}", consumes = { MediaType.APPLICATION_YAML, MediaType.TEXT_PLAIN })
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Apps" }, summary = "Replace an app's YAML definition")
    public App updateApp(
        HttpRequest<?> request,
        @Parameter(description = "The app namespace") @PathVariable String namespace,
        @Parameter(description = "The app id") @PathVariable String id,
        @RequestBody(description = "The app YAML") @Body String source) {
        return appService.update(grants(request), namespace, id, source);
    }

    @Delete("/{namespace}/{id}")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Apps" }, summary = "Delete an app")
    public HttpResponse<Void> deleteApp(
        HttpRequest<?> request,
        @Parameter(description = "The app namespace") @PathVariable String namespace,
        @Parameter(description = "The app id") @PathVariable String id) {
        appService.delete(grants(request), namespace, id);
        return HttpResponse.noContent();
    }

    private UserGrants grants(HttpRequest<?> request) {
        AuthenticatedUser user = AuthenticatedUser.from(request)
            .orElseThrow(() -> new ForbiddenException("The request is not authenticated."));
        return UserGrants.of(request, user, iamService);
    }
}

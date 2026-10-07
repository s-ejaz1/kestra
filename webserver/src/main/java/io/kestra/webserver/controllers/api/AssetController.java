package io.kestra.webserver.controllers.api;

import io.kestra.core.exceptions.ForbiddenException;
import io.kestra.core.http.KestraMediaTypes;
import io.kestra.core.models.assets.Asset;
import io.kestra.core.models.assets.AssetUsage;
import io.kestra.webserver.responses.PagedResults;
import io.kestra.webserver.services.AssetCatalogService;
import io.kestra.webserver.services.AuthenticatedUser;
import io.kestra.webserver.services.IamService;
import io.kestra.webserver.services.UserGrants;
import io.kestra.webserver.utils.PageableUtils;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Controller("/api/v1/{tenant}/assets")
public class AssetController {
    private final AssetCatalogService assetCatalogService;
    private final IamService iamService;

    @Inject
    public AssetController(AssetCatalogService assetCatalogService, IamService iamService) {
        this.assetCatalogService = assetCatalogService;
        this.iamService = iamService;
    }

    @Get("/search")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Assets" }, summary = "Search the assets the caller can see")
    public PagedResults<Asset> searchAssets(
        HttpRequest<?> request,
        @Parameter(description = "The current page") @QueryValue(defaultValue = "1") @Min(1) int page,
        @Parameter(description = "The current page size") @QueryValue(defaultValue = "25") @Min(1) int size,
        @Parameter(description = "A text matched against the asset id and display name") @Nullable @QueryValue String q,
        @Parameter(description = "A namespace, matching its children too") @Nullable @QueryValue String namespace,
        @Parameter(description = "The asset type") @Nullable @QueryValue String type) {
        return PagedResults.of(assetCatalogService.search(grants(request), PageableUtils.from(page, size), q, namespace, type));
    }

    @Get("/{id}")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Assets" }, summary = "Retrieve an asset")
    public Asset getAsset(HttpRequest<?> request, @Parameter(description = "The asset id") @PathVariable String id) {
        return assetCatalogService.get(grants(request), id);
    }

    @Post(consumes = { KestraMediaTypes.APPLICATION_X_YAML, MediaType.APPLICATION_YAML, MediaType.TEXT_PLAIN })
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Assets" }, summary = "Create or update an asset from its YAML definition")
    public Asset saveAsset(HttpRequest<?> request, @RequestBody(description = "The asset YAML") @Body String source) {
        return assetCatalogService.save(grants(request), source);
    }

    @Delete("/{id}")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Assets" }, summary = "Delete an asset")
    public HttpResponse<Void> deleteAsset(HttpRequest<?> request, @Parameter(description = "The asset id") @PathVariable String id) {
        assetCatalogService.delete(grants(request), id);
        return HttpResponse.noContent();
    }

    @Get("/{id}/usages")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Assets" }, summary = "List the task runs that read or wrote an asset, newest first")
    public PagedResults<AssetUsage> listAssetUsages(
        HttpRequest<?> request,
        @Parameter(description = "The asset id") @PathVariable String id,
        @Parameter(description = "The current page") @QueryValue(defaultValue = "1") @Min(1) int page,
        @Parameter(description = "The current page size") @QueryValue(defaultValue = "25") @Min(1) int size) {
        return PagedResults.of(assetCatalogService.usages(grants(request), PageableUtils.from(page, size), id));
    }

    @Get("/{id}/lineage")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Assets" }, summary = "Retrieve the assets an asset is derived from and the ones derived from it")
    public AssetCatalogService.Lineage getAssetLineage(
        HttpRequest<?> request,
        @Parameter(description = "The asset id") @PathVariable String id,
        @Parameter(description = "How many hops to follow each way") @QueryValue(defaultValue = "3") @Min(1) @Max(10) int depth) {
        return assetCatalogService.lineage(grants(request), id, depth);
    }

    private UserGrants grants(HttpRequest<?> request) {
        AuthenticatedUser user = AuthenticatedUser.from(request)
            .orElseThrow(() -> new ForbiddenException("The request is not authenticated."));
        return UserGrants.of(request, user, iamService);
    }
}

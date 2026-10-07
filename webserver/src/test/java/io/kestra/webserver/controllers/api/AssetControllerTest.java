package io.kestra.webserver.controllers.api;

import org.junit.jupiter.api.Test;

import io.kestra.core.http.KestraMediaTypes;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.assets.Asset;
import io.kestra.core.models.assets.AssetUsage;
import io.kestra.core.models.iam.IamBinding;
import io.kestra.core.utils.IdUtils;
import io.kestra.webserver.responses.PagedResults;
import io.kestra.webserver.services.AssetCatalogService;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;

@KestraTest(startRunner = true)
class AssetControllerTest {
    private static final String PASSWORD = "Passw0rdLong";

    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Test
    void shouldRecordDeclaredAssetsUsagesAndLineageWhenAFlowRuns() {
        String suffix = IdUtils.create().toLowerCase();
        String namespace = "assets" + suffix;
        createFlow(namespace, suffix);

        client.toBlocking().exchange(HttpRequest.POST("/api/v1/main/executions/" + namespace + "/etl?wait=true", null));

        Asset output = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/assets/clean_" + suffix), Asset.class);
        PagedResults<AssetUsage> usages = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/main/assets/clean_" + suffix + "/usages"),
            Argument.of(PagedResults.class, AssetUsage.class)
        );
        AssetCatalogService.Lineage lineage = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/main/assets/clean_" + suffix + "/lineage"),
            AssetCatalogService.Lineage.class
        );

        assertThat(output.getType()).isEqualTo("io.kestra.plugin.ee.assets.Table");
        assertThat(output.getNamespace()).isEqualTo(namespace);
        assertThat(usages.getResults()).extracting(AssetUsage::getDirection, AssetUsage::getTaskId)
            .containsExactly(tuple(AssetUsage.Direction.OUTPUT, "transform"));
        assertThat(lineage.edges()).extracting(AssetCatalogService.Edge::source, AssetCatalogService.Edge::target)
            .containsExactly(tuple("raw_" + suffix, "clean_" + suffix));
    }

    @Test
    void shouldHideAssetsOutsideTheNamespacesAUserCanRead() {
        String suffix = IdUtils.create().toLowerCase();
        String namespace = "assets" + suffix;
        client.toBlocking().exchange(yaml(HttpRequest.POST("/api/v1/main/assets", """
            id: report_%s
            type: io.kestra.plugin.ee.assets.Table
            namespace: %s
            """.formatted(suffix, namespace))));
        IamController.ApiUser viewer = client.toBlocking().retrieve(
            HttpRequest.POST("/api/v1/main/iam/users", new IamController.UserRequest(suffix + "@kestra.io", null, null, PASSWORD)),
            IamController.ApiUser.class
        );
        client.toBlocking().retrieve(HttpRequest.POST("/api/v1/main/iam/bindings", new IamController.BindingRequest(viewer.id(), "viewer", "other" + suffix)), IamBinding.class);

        PagedResults<Asset> search = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/main/assets/search?q=report_" + suffix).basicAuth(viewer.email(), PASSWORD),
            Argument.of(PagedResults.class, Asset.class)
        );
        HttpClientResponseException read = (HttpClientResponseException) catchThrowable(
            () -> client.toBlocking().exchange(HttpRequest.GET("/api/v1/main/assets/report_" + suffix).basicAuth(viewer.email(), PASSWORD))
        );

        assertThat(search.getResults()).isEmpty();
        assertThat(read.getStatus().getCode()).isEqualTo(HttpStatus.FORBIDDEN.getCode());
    }

    private void createFlow(String namespace, String suffix) {
        client.toBlocking().exchange(yaml(HttpRequest.POST("/api/v1/main/flows", """
            id: etl
            namespace: %1$s
            tasks:
              - id: transform
                type: io.kestra.plugin.core.log.Log
                message: transforming
                assets:
                  inputs:
                    - id: raw_%2$s
                  outputs:
                    - id: clean_%2$s
                      type: io.kestra.plugin.ee.assets.Table
                      namespace: %1$s
            """.formatted(namespace, suffix))));
    }

    private static <T> MutableHttpRequest<T> yaml(MutableHttpRequest<T> request) {
        return request.contentType(KestraMediaTypes.APPLICATION_X_YAML);
    }
}

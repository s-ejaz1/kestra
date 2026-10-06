package io.kestra.webserver.filter;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.iam.IamBinding;
import io.kestra.core.utils.IdUtils;
import io.kestra.webserver.controllers.api.IamController;
import io.kestra.webserver.responses.PagedResults;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@KestraTest
class IamAuthorizationFilterTest {
    private static final String PASSWORD = "Passw0rdLong";

    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Test
    void shouldOnlyListFlowsOfBoundNamespace() {
        String suffix = IdUtils.create().toLowerCase();
        createFlow("team" + suffix + ".data", "allowed");
        createFlow("other" + suffix, "hidden");
        IamController.ApiUser user = userWithRole("viewer", "team" + suffix);

        PagedResults<Map<String, Object>> flows = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/main/flows/search?size=100").basicAuth(user.email(), PASSWORD),
            Argument.of(PagedResults.class, Argument.mapOf(String.class, Object.class))
        );

        assertThat(flows.getResults()).extracting(flow -> flow.get("id")).contains("allowed").doesNotContain("hidden");
    }

    @Test
    void shouldForbidWriteWithReadOnlyRole() {
        String namespace = "readonly" + IdUtils.create().toLowerCase();
        IamController.ApiUser user = userWithRole("viewer", namespace);

        HttpClientResponseException exception = (HttpClientResponseException) catchThrowable(
            () -> client.toBlocking().exchange(
                HttpRequest.POST("/api/v1/main/flows", flowSource(namespace, "denied")).contentType(MediaType.APPLICATION_YAML).basicAuth(user.email(), PASSWORD)
            )
        );

        assertThat(exception.getStatus().getCode()).isEqualTo(HttpStatus.FORBIDDEN.getCode());
        assertThat(exception.getResponse().getBody(String.class)).hasValueSatisfying(body -> assertThat(body).contains("CREATE").contains("FLOW").contains(user.email()));
    }

    @Test
    void shouldAllowWriteInBoundNamespaceOnlyForEditor() {
        String namespace = "editor" + IdUtils.create().toLowerCase();
        IamController.ApiUser user = userWithRole("editor", namespace);

        HttpStatus inside = client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/main/flows", flowSource(namespace + ".sub", "created")).contentType(MediaType.APPLICATION_YAML).basicAuth(user.email(), PASSWORD)
        ).getStatus();
        HttpClientResponseException outside = (HttpClientResponseException) catchThrowable(
            () -> client.toBlocking().exchange(
                HttpRequest.PUT("/api/v1/main/namespaces/elsewhere/kv/key", "\"value\"").contentType(MediaType.TEXT_PLAIN).basicAuth(user.email(), PASSWORD)
            )
        );

        assertThat(inside.getCode()).isEqualTo(HttpStatus.OK.getCode());
        assertThat(outside.getStatus().getCode()).isEqualTo(HttpStatus.FORBIDDEN.getCode());
    }

    @Test
    void shouldTreatReadOnlyPostRoutesAsReads() {
        IamController.ApiUser user = userWithRole("viewer", "autocomplete" + IdUtils.create().toLowerCase());

        HttpStatus status = client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/main/namespaces/autocomplete", Map.of("q", "")).basicAuth(user.email(), PASSWORD)
        ).getStatus();

        assertThat(status.getCode()).isEqualTo(HttpStatus.OK.getCode());
    }

    @Test
    void shouldReserveUnmappedAdminRoutesToAdmins() {
        IamController.ApiUser user = userWithRole("editor", null);

        HttpClientResponseException exception = (HttpClientResponseException) catchThrowable(
            () -> client.toBlocking().exchange(HttpRequest.GET("/api/v1/main/concurrency-limit/search").basicAuth(user.email(), PASSWORD))
        );

        assertThat(exception.getStatus().getCode()).isEqualTo(HttpStatus.FORBIDDEN.getCode());
    }

    private void createFlow(String namespace, String id) {
        client.toBlocking().exchange(HttpRequest.POST("/api/v1/main/flows", flowSource(namespace, id)).contentType(MediaType.APPLICATION_YAML));
    }

    private static String flowSource(String namespace, String id) {
        return """
            id: %s
            namespace: %s
            tasks:
              - id: log
                type: io.kestra.plugin.core.log.Log
                message: hello
            """.formatted(id, namespace);
    }

    private IamController.ApiUser userWithRole(String roleId, String namespace) {
        IamController.ApiUser user = client.toBlocking().retrieve(
            HttpRequest.POST("/api/v1/main/iam/users", new IamController.UserRequest(IdUtils.create().toLowerCase() + "@kestra.io", null, null, PASSWORD)),
            IamController.ApiUser.class
        );
        client.toBlocking().retrieve(HttpRequest.POST("/api/v1/main/iam/bindings", new IamController.BindingRequest(user.id(), roleId, namespace)), IamBinding.class);
        return user;
    }
}

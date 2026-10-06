package io.kestra.webserver.controllers.api;

import org.junit.jupiter.api.Test;

import io.kestra.core.http.KestraMediaTypes;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.apps.App;
import io.kestra.core.models.iam.IamBinding;
import io.kestra.core.utils.IdUtils;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@KestraTest
class AppControllerTest {
    private static final String PASSWORD = "Passw0rdLong";

    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Test
    void shouldCreateReadAndUpdateApp() {
        String namespace = "apps" + IdUtils.create().toLowerCase();
        createFlow(namespace);

        App created = client.toBlocking().retrieve(yaml(HttpRequest.POST("/api/v1/main/apps", appSource(namespace, "Markdown"))), App.class);
        App updated = client.toBlocking().retrieve(
            yaml(HttpRequest.PUT("/api/v1/main/apps/" + namespace + "/form", appSource(namespace, "Logs"))),
            App.class
        );

        assertThat(created.getFlowId()).isEqualTo("hello");
        assertThat(created.getLayout()).extracting(App.Layout::on).containsExactly(App.Stage.OPEN);
        assertThat(updated.getSource()).contains("Logs");
        assertThat(client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/apps/" + namespace + "/form"), App.class).getSource()).contains("Logs");
    }

    @Test
    void shouldRejectUnknownBlockAndMissingFlow() {
        String namespace = "apps" + IdUtils.create().toLowerCase();

        HttpClientResponseException exception = (HttpClientResponseException) catchThrowable(
            () -> client.toBlocking().exchange(yaml(HttpRequest.POST("/api/v1/main/apps", appSource(namespace, "Video"))))
        );

        assertThat(exception.getStatus().getCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.getCode());
        assertThat(exception.getResponse().getBody(String.class)).hasValueSatisfying(body -> assertThat(body).contains("Video").contains("does not exist"));
    }

    @Test
    void shouldLetViewerOpenButNotCreateApps() {
        String namespace = "apps" + IdUtils.create().toLowerCase();
        createFlow(namespace);
        client.toBlocking().exchange(yaml(HttpRequest.POST("/api/v1/main/apps", appSource(namespace, "Markdown"))));
        IamController.ApiUser viewer = client.toBlocking().retrieve(
            HttpRequest.POST("/api/v1/main/iam/users", new IamController.UserRequest(IdUtils.create().toLowerCase() + "@kestra.io", null, null, PASSWORD)),
            IamController.ApiUser.class
        );
        client.toBlocking().retrieve(HttpRequest.POST("/api/v1/main/iam/bindings", new IamController.BindingRequest(viewer.id(), "viewer", namespace)), IamBinding.class);

        HttpStatus read = client.toBlocking().exchange(HttpRequest.GET("/api/v1/main/apps/" + namespace + "/form").basicAuth(viewer.email(), PASSWORD)).getStatus();
        HttpClientResponseException write = (HttpClientResponseException) catchThrowable(
            () -> client.toBlocking().exchange(yaml(HttpRequest.PUT("/api/v1/main/apps/" + namespace + "/form", appSource(namespace, "Logs")).basicAuth(viewer.email(), PASSWORD)))
        );

        assertThat(read.getCode()).isEqualTo(HttpStatus.OK.getCode());
        assertThat(write.getStatus().getCode()).isEqualTo(HttpStatus.FORBIDDEN.getCode());
    }

    private void createFlow(String namespace) {
        client.toBlocking().exchange(yaml(HttpRequest.POST("/api/v1/main/flows", """
            id: hello
            namespace: %s
            tasks:
              - id: log
                type: io.kestra.plugin.core.log.Log
                message: hello
            """.formatted(namespace))));
    }

    private static String appSource(String namespace, String blockType) {
        return """
            id: form
            namespace: %s
            flowId: hello
            layout:
              - on: OPEN
                blocks:
                  - type: %s
                    content: Hello
            """.formatted(namespace, blockType);
    }

    private static <T> MutableHttpRequest<T> yaml(MutableHttpRequest<T> request) {
        return request.contentType(KestraMediaTypes.APPLICATION_X_YAML);
    }
}

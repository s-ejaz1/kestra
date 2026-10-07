package io.kestra.webserver.controllers.api;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.http.KestraMediaTypes;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.iam.IamBinding;
import io.kestra.core.models.validations.ValidateConstraintViolation;
import io.kestra.core.utils.IdUtils;
import io.kestra.webserver.services.PolicyService;

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

@KestraTest(startRunner = true)
class PolicyControllerTest {
    private static final String PASSWORD = "Passw0rdLong";

    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Test
    void shouldInjectDefaultsAndPinnedLabelsIntoTheFlowsOfTheNamespace() {
        String namespace = "policies" + IdUtils.create().toLowerCase();
        createPolicy("""
            id: defaults-%1$s
            namespace: %1$s
            enforcement: ENFORCE
            rules:
              - type: Defaults
                target: io.kestra.plugin.core.log.Log
                values:
                  message: from the policy
              - type: Labels
                override: true
                labels:
                  team: data
            """.formatted(namespace));

        client.toBlocking().exchange(yaml(HttpRequest.POST("/api/v1/main/flows", """
            id: hello
            namespace: %s
            tasks:
              - id: log
                type: io.kestra.plugin.core.log.Log
            """.formatted(namespace))));
        Execution execution = client.toBlocking().retrieve(
            HttpRequest.POST("/api/v1/main/executions/" + namespace + "/hello?wait=true&labels=team:ops", null),
            Execution.class
        );
        PolicyService.Preview preview = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/main/policies/preview/" + namespace + "/hello"),
            PolicyService.Preview.class
        );

        assertThat(execution.getState().getCurrent().isSuccess()).isTrue();
        assertThat(execution.getLabels()).contains(new Label("team", "data"));
        assertThat(preview.source()).contains("from the policy");
        assertThat(preview.changes()).hasSize(2);
    }

    @Test
    void shouldBlockAFlowOnlyWhenThePolicyIsEnforced() {
        String enforced = "policies" + IdUtils.create().toLowerCase();
        String audited = "policies" + IdUtils.create().toLowerCase();
        createPolicy(blockPolicy(enforced, "ENFORCE"));
        createPolicy(blockPolicy(audited, "AUDIT"));

        HttpClientResponseException blocked = (HttpClientResponseException) catchThrowable(
            () -> client.toBlocking().exchange(yaml(HttpRequest.POST("/api/v1/main/flows", returnFlow(enforced))))
        );
        HttpStatus saved = client.toBlocking().exchange(yaml(HttpRequest.POST("/api/v1/main/flows", returnFlow(audited)))).getStatus();
        List<ValidateConstraintViolation> validation = client.toBlocking().retrieve(
            yaml(HttpRequest.POST("/api/v1/main/flows/validate", returnFlow(audited))),
            Argument.listOf(ValidateConstraintViolation.class)
        );

        assertThat(blocked.getStatus().getCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.getCode());
        assertThat(blocked.getResponse().getBody(String.class)).hasValueSatisfying(body -> assertThat(body).contains("io.kestra.plugin.core.debug.Return"));
        assertThat(saved.getCode()).isEqualTo(HttpStatus.OK.getCode());
        assertThat(validation.getFirst().getWarnings()).anySatisfy(warning -> assertThat(warning).startsWith("[audit]"));
    }

    @Test
    void shouldReservePoliciesToAdministrators() {
        IamController.ApiUser viewer = client.toBlocking().retrieve(
            HttpRequest.POST("/api/v1/main/iam/users", new IamController.UserRequest(IdUtils.create().toLowerCase() + "@kestra.io", null, null, PASSWORD)),
            IamController.ApiUser.class
        );
        client.toBlocking().retrieve(HttpRequest.POST("/api/v1/main/iam/bindings", new IamController.BindingRequest(viewer.id(), "editor", null)), IamBinding.class);

        HttpClientResponseException denied = (HttpClientResponseException) catchThrowable(
            () -> client.toBlocking().exchange(HttpRequest.GET("/api/v1/main/policies").basicAuth(viewer.email(), PASSWORD))
        );

        assertThat(denied.getStatus().getCode()).isEqualTo(HttpStatus.FORBIDDEN.getCode());
    }

    private void createPolicy(String source) {
        client.toBlocking().exchange(yaml(HttpRequest.POST("/api/v1/main/policies", source)));
    }

    private static String blockPolicy(String namespace, String enforcement) {
        return """
            id: block-%1$s
            namespace: %1$s
            enforcement: %2$s
            rules:
              - type: BlockPlugins
                plugins:
                  - io.kestra.plugin.core.debug.*
            """.formatted(namespace, enforcement);
    }

    private static String returnFlow(String namespace) {
        return """
            id: debug
            namespace: %s
            tasks:
              - id: value
                type: io.kestra.plugin.core.debug.Return
                format: hello
            """.formatted(namespace);
    }

    private static <T> MutableHttpRequest<T> yaml(MutableHttpRequest<T> request) {
        return request.contentType(KestraMediaTypes.APPLICATION_X_YAML);
    }
}

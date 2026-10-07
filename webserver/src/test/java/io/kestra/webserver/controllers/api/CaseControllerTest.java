package io.kestra.webserver.controllers.api;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.http.KestraMediaTypes;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.cases.Case;
import io.kestra.core.models.cases.CaseActivity;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.iam.IamBinding;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.IdUtils;
import io.kestra.webserver.responses.PagedResults;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@KestraTest(startRunner = true)
class CaseControllerTest {
    private static final String PASSWORD = "Passw0rdLong";

    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Test
    void shouldLinkFailedExecutionsWhileTheCaseIsOpenAndRecordTheTimeline() {
        String namespace = "cases" + IdUtils.create().toLowerCase();
        createFailingFlow(namespace);
        Case opened = client.toBlocking().retrieve(
            HttpRequest.POST("/api/v1/main/cases", request(namespace, "broken", true)),
            Case.class
        );

        String linkedId = runFlow(namespace);
        Await.await().atMost(Duration.ofSeconds(30)).until(() -> get(opened.getId()).getExecutions().size() == 1);
        Case resolved = client.toBlocking().retrieve(
            HttpRequest.POST("/api/v1/main/cases/" + opened.getId() + "/status", new CaseController.StatusRequest(Case.Status.RESOLVED, "Fixed the credentials")),
            Case.class
        );
        runFlow(namespace);
        PagedResults<CaseActivity> timeline = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/main/cases/" + opened.getId() + "/activities"),
            Argument.of(PagedResults.class, CaseActivity.class)
        );

        assertThat(opened.getDueDate()).isEqualTo(opened.getCreated().plus(Case.Severity.HIGH.sla));
        assertThat(resolved.getResolvedDate()).isNotNull();
        assertThat(get(opened.getId()).getExecutions()).extracting(Case.LinkedExecution::executionId).containsExactly(linkedId);
        assertThat(timeline.getResults()).extracting(CaseActivity::getType)
            .containsExactly(CaseActivity.Type.CREATED, CaseActivity.Type.EXECUTION_LINKED, CaseActivity.Type.STATUS_CHANGED);
        assertThat(timeline.getResults().get(1).getAuthor()).isEqualTo(CaseActivity.SYSTEM_AUTHOR);
        assertThat(timeline.getResults().get(2).getMessage()).isEqualTo("Fixed the credentials");
    }

    @Test
    void shouldLetAViewerReadButNotChangeACase() {
        String namespace = "cases" + IdUtils.create().toLowerCase();
        Case opened = client.toBlocking().retrieve(HttpRequest.POST("/api/v1/main/cases", request(namespace, null, false)), Case.class);
        IamController.ApiUser viewer = client.toBlocking().retrieve(
            HttpRequest.POST("/api/v1/main/iam/users", new IamController.UserRequest(IdUtils.create().toLowerCase() + "@kestra.io", null, null, PASSWORD)),
            IamController.ApiUser.class
        );
        client.toBlocking().retrieve(HttpRequest.POST("/api/v1/main/iam/bindings", new IamController.BindingRequest(viewer.id(), "viewer", namespace)), IamBinding.class);

        HttpStatus read = client.toBlocking().exchange(HttpRequest.GET("/api/v1/main/cases/" + opened.getId()).basicAuth(viewer.email(), PASSWORD)).getStatus();
        HttpClientResponseException comment = (HttpClientResponseException) catchThrowable(() -> client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/main/cases/" + opened.getId() + "/comments", new CaseController.CommentRequest("Looking into it")).basicAuth(viewer.email(), PASSWORD)
        ));

        assertThat(read.getCode()).isEqualTo(HttpStatus.OK.getCode());
        assertThat(comment.getStatus().getCode()).isEqualTo(HttpStatus.FORBIDDEN.getCode());
    }

    private Case get(String id) {
        return client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/cases/" + id), Case.class);
    }

    private String runFlow(String namespace) {
        return client.toBlocking().retrieve(HttpRequest.POST("/api/v1/main/executions/" + namespace + "/broken?wait=true", null), Execution.class).getId();
    }

    private void createFailingFlow(String namespace) {
        client.toBlocking().exchange(HttpRequest.POST("/api/v1/main/flows", """
            id: broken
            namespace: %s
            tasks:
              - id: fail
                type: io.kestra.plugin.core.execution.Fail
            """.formatted(namespace)).contentType(KestraMediaTypes.APPLICATION_X_YAML));
    }

    private static CaseController.CaseRequest request(String namespace, String flowId, boolean autoLink) {
        return new CaseController.CaseRequest("Orders sync is failing", "The Amazon token expired.", Case.Severity.HIGH, namespace, flowId, autoLink, List.of(), List.of("amazon"), null, null);
    }
}

package io.kestra.webserver.controllers.api;

import org.junit.jupiter.api.Test;

import io.kestra.core.http.KestraMediaTypes;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.iam.IamBinding;
import io.kestra.core.test.TestState;
import io.kestra.core.test.TestSuiteRunResult;
import io.kestra.core.test.flow.UnitTestResult;
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
import static org.assertj.core.api.Assertions.tuple;

@KestraTest(startRunner = true)
class TestSuiteControllerTest {
    private static final String PASSWORD = "Passw0rdLong";

    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Test
    void shouldRunTestCasesWithFixturesAndReportFailedAssertions() {
        String namespace = createFlowAndSuite();

        TestSuiteRunResult run = client.toBlocking().retrieve(HttpRequest.POST("/api/v1/main/tests/" + namespace + "/suite/run", ""), TestSuiteRunResult.class);
        TestSuiteController.TestSuiteWithLastRun fetched = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/main/tests/" + namespace + "/suite"),
            TestSuiteController.TestSuiteWithLastRun.class
        );

        assertThat(run.state()).isEqualTo(TestState.FAILED);
        assertThat(run.results()).extracting(UnitTestResult::testId, UnitTestResult::state)
            .containsExactly(
                tuple("mocked", TestState.SUCCESS),
                tuple("wrong", TestState.FAILED)
            );
        assertThat(fetched.lastRun()).isNotNull();
        assertThat(fetched.lastRun().id()).isEqualTo(run.id());
    }

    @Test
    void shouldLetViewerReadButNotRunTestSuites() {
        String namespace = createFlowAndSuite();
        IamController.ApiUser viewer = client.toBlocking().retrieve(
            HttpRequest.POST("/api/v1/main/iam/users", new IamController.UserRequest(IdUtils.create().toLowerCase() + "@kestra.io", null, null, PASSWORD)),
            IamController.ApiUser.class
        );
        client.toBlocking().retrieve(HttpRequest.POST("/api/v1/main/iam/bindings", new IamController.BindingRequest(viewer.id(), "viewer", namespace)), IamBinding.class);

        HttpStatus read = client.toBlocking().exchange(HttpRequest.GET("/api/v1/main/tests/" + namespace + "/suite").basicAuth(viewer.email(), PASSWORD)).getStatus();
        HttpClientResponseException run = (HttpClientResponseException) catchThrowable(
            () -> client.toBlocking().exchange(HttpRequest.POST("/api/v1/main/tests/" + namespace + "/suite/run", "").basicAuth(viewer.email(), PASSWORD))
        );

        assertThat(read.getCode()).isEqualTo(HttpStatus.OK.getCode());
        assertThat(run.getStatus().getCode()).isEqualTo(HttpStatus.FORBIDDEN.getCode());
    }

    private String createFlowAndSuite() {
        String namespace = "tests" + IdUtils.create().toLowerCase();
        client.toBlocking().exchange(yaml(HttpRequest.POST("/api/v1/main/flows", """
            id: greet
            namespace: %s
            inputs:
              - id: name
                type: STRING
            tasks:
              - id: fetch
                type: io.kestra.plugin.core.execution.Fail
              - id: greeting
                type: io.kestra.plugin.core.debug.Return
                format: "Hello {{ inputs.name }} {{ outputs.fetch.value }}"
            """.formatted(namespace))));
        client.toBlocking().exchange(yaml(HttpRequest.POST("/api/v1/main/tests", """
            id: suite
            namespace: %s
            flowId: greet
            testCases:
              - id: mocked
                type: io.kestra.core.tests.flow.UnitTest
                fixtures:
                  inputs:
                    name: Kestra
                  tasks:
                    - id: fetch
                      outputs:
                        value: from-fixture
                assertions:
                  - value: "{{ outputs.greeting.value }}"
                    equalTo: Hello Kestra from-fixture
              - id: wrong
                type: io.kestra.core.tests.flow.UnitTest
                fixtures:
                  inputs:
                    name: Kestra
                  tasks:
                    - id: fetch
                      outputs:
                        value: from-fixture
                assertions:
                  - value: "{{ outputs.greeting.value }}"
                    equalTo: Something else
            """.formatted(namespace))));
        return namespace;
    }

    private static <T> MutableHttpRequest<T> yaml(MutableHttpRequest<T> request) {
        return request.contentType(KestraMediaTypes.APPLICATION_X_YAML);
    }
}

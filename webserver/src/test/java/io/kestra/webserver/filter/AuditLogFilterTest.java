package io.kestra.webserver.filter;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.audit.AuditLog;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.IdUtils;
import io.kestra.webserver.responses.PagedResults;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class AuditLogFilterTest {
    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Test
    void shouldRecordChangesButNotReads() throws Exception {
        String namespace = "audit" + IdUtils.create().toLowerCase();
        client.toBlocking().exchange(HttpRequest.PUT("/api/v1/main/namespaces/" + namespace + "/kv/key", "\"value\"").contentType(MediaType.TEXT_PLAIN));
        client.toBlocking().exchange(HttpRequest.POST("/api/v1/main/namespaces/autocomplete", Map.of("q", namespace)));

        Await.until(() -> search(namespace).getTotal() > 0, Duration.ofMillis(100), Duration.ofSeconds(10));

        PagedResults<AuditLog> logs = search(namespace);
        assertThat(logs.getResults()).hasSize(1);
        AuditLog auditLog = logs.getResults().getFirst();
        assertThat(auditLog.getMethod()).isEqualTo("PUT");
        assertThat(auditLog.getResource()).isEqualTo("KVSTORE");
        assertThat(auditLog.getNamespace()).isEqualTo(namespace);
        assertThat(auditLog.getUserEmail()).isNotBlank();
    }

    private PagedResults<AuditLog> search(String namespace) {
        return client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/main/audit-logs/search?namespace=" + namespace),
            Argument.of(PagedResults.class, AuditLog.class)
        );
    }
}

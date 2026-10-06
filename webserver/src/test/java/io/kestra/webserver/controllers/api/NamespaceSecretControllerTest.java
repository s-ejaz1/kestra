package io.kestra.webserver.controllers.api;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.secret.PersistedSecretMetadata;
import io.kestra.core.repositories.SecretMetadataRepositoryInterface;
import io.kestra.core.secret.SecretNotFoundException;
import io.kestra.core.secret.SecretService;
import io.kestra.core.storages.StorageInterface;
import io.kestra.webserver.filter.TestAuthFilter;
import io.kestra.webserver.models.api.secret.ApiSecretListResponse;
import io.kestra.webserver.models.api.secret.ApiSecretMeta;
import io.kestra.webserver.services.BasicAuthService;
import io.kestra.webserver.services.SecretStoreService;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;

@KestraTest
class NamespaceSecretControllerTest {
    private static final String NAMESPACE = "io.kestra.unittest.storedsecrets";
    private static final String BASE = "/api/v1/main/namespaces/" + NAMESPACE + "/secrets";

    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Inject
    SecretService<String> secretService;

    @Inject
    StorageInterface storageInterface;

    @Inject
    BasicAuthService basicAuthService;

    @Inject
    SecretMetadataRepositoryInterface secretMetadataRepository;

    @Inject
    SecretStoreService secretStoreService;

    @AfterEach
    void deleteStoredSecrets() throws Exception {
        List<QueryFilter> filters = List.of(QueryFilter.builder().field(QueryFilter.Field.NAMESPACE).operation(QueryFilter.Op.STARTS_WITH).value(NAMESPACE).build());
        for (PersistedSecretMetadata secret : secretMetadataRepository.find(MAIN_TENANT, filters)) {
            secretStoreService.delete(MAIN_TENANT, secret.getNamespace(), secret.getName());
        }
    }

    @Test
    void shouldStoreEncryptedSecretResolvableFromChildNamespace() throws Exception {
        String namespace = NAMESPACE + ".listing";

        ApiSecretMeta created = client.toBlocking().retrieve(
            HttpRequest.PUT("/api/v1/main/namespaces/" + namespace + "/secrets", new NamespaceSecretController.SecretRequest("db_password", "s3cr3t", "The database password", List.of(new PersistedSecretMetadata.Tag("team", "data")))),
            ApiSecretMeta.class
        );

        assertThat(created.getKey()).isEqualTo("DB_PASSWORD");
        assertThat(secretService.findSecret(MAIN_TENANT, namespace + ".child", "db_password")).isEqualTo("s3cr3t");
        try (InputStream stored = storageInterface.get(MAIN_TENANT, namespace, SecretService.storageUri(namespace, "db_password"))) {
            assertThat(new String(stored.readAllBytes(), StandardCharsets.UTF_8)).doesNotContain("s3cr3t");
        }

        ApiSecretListResponse<ApiSecretMeta> listed = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/main/secrets?filters[namespace][EQUALS]=" + namespace),
            ApiSecretListResponse.class
        );
        assertThat(listed.results()).extracting(ApiSecretMeta::getKey, ApiSecretMeta::getNamespace, ApiSecretMeta::getDescription)
            .containsExactly(tuple("DB_PASSWORD", namespace, "The database password"));

        Map<String, List<String>> inherited = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/main/namespaces/" + namespace + ".child/inherited-secrets"),
            Map.class
        );
        assertThat(inherited.get(namespace)).containsExactly("DB_PASSWORD");
    }

    @Test
    void shouldKeepValueWhenOnlyMetadataIsPatched() throws Exception {
        client.toBlocking().retrieve(HttpRequest.PUT(BASE, new NamespaceSecretController.SecretRequest("api_token", "token-1", null, null)), ApiSecretMeta.class);

        ApiSecretMeta patched = client.toBlocking().retrieve(
            HttpRequest.PATCH(BASE + "/api_token", new NamespaceSecretController.SecretMetadataRequest("Rotated monthly", null)),
            ApiSecretMeta.class
        );

        assertThat(patched.getDescription()).isEqualTo("Rotated monthly");
        assertThat(secretService.findSecret(MAIN_TENANT, NAMESPACE, "API_TOKEN")).isEqualTo("token-1");
    }

    @Test
    void shouldNoLongerResolveDeletedSecret() throws Exception {
        client.toBlocking().retrieve(HttpRequest.PUT(BASE, new NamespaceSecretController.SecretRequest("to_delete", "value", null, null)), ApiSecretMeta.class);

        HttpStatus status = client.toBlocking().exchange(HttpRequest.DELETE(BASE + "/to_delete")).getStatus();

        assertThat(status.getCode()).isEqualTo(HttpStatus.NO_CONTENT.getCode());
        assertThatThrownBy(() -> secretService.findSecret(MAIN_TENANT, NAMESPACE, "to_delete")).isInstanceOf(SecretNotFoundException.class);
    }

    @Test
    void shouldReturnNotFoundWhenDeletingUnknownSecret() {
        HttpClientResponseException exception = (HttpClientResponseException) catchThrowable(
            () -> client.toBlocking().exchange(HttpRequest.DELETE(BASE + "/unknown"))
        );

        assertThat(exception.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
    }

    @Test
    void shouldRejectKeyWithInvalidCharacters() {
        HttpClientResponseException exception = (HttpClientResponseException) catchThrowable(
            () -> client.toBlocking().exchange(HttpRequest.PUT(BASE, new NamespaceSecretController.SecretRequest("not a key", "value", null, null)))
        );

        assertThat(exception.getStatus().getCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.getCode());
    }

    @Test
    void shouldDenyWriteWhenUnauthenticated() {
        if (basicAuthService.credentials() == null) {
            basicAuthService.init();
        }
        TestAuthFilter.ENABLED = false;
        try {
            HttpClientResponseException exception = (HttpClientResponseException) catchThrowable(
                () -> client.toBlocking().exchange(HttpRequest.PUT(BASE, new NamespaceSecretController.SecretRequest("anonymous", "value", null, null)))
            );

            assertThat(exception.getStatus().getCode()).isEqualTo(HttpStatus.UNAUTHORIZED.getCode());
        } finally {
            TestAuthFilter.ENABLED = true;
        }
    }
}

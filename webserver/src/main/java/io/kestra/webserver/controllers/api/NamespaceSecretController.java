package io.kestra.webserver.controllers.api;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.kestra.core.models.secret.PersistedSecretMetadata;
import io.kestra.core.tenant.TenantService;
import io.kestra.webserver.models.api.secret.ApiSecretMeta;
import io.kestra.webserver.services.SecretStoreService;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Patch;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Put;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Controller("/api/v1/{tenant}/namespaces")
public class NamespaceSecretController<META extends ApiSecretMeta> {
    @Inject
    protected TenantService tenantService;

    @Inject
    protected SecretStoreService secretStoreService;

    @Get(uri = "{namespace}/inherited-secrets")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Namespaces" }, summary = "List inherited secrets")
    public HttpResponse<Map<String, Set<String>>> getInheritedSecrets(
        @Parameter(description = "The namespace id") @PathVariable String namespace) throws IllegalArgumentException, IOException {
        return HttpResponse.ok(secretStoreService.inheritedSecrets(tenantService.resolveTenant(), namespace));
    }

    @Put(uri = "{namespace}/secrets")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Namespaces" }, summary = "Create or replace a secret of a namespace")
    public ApiSecretMeta putSecret(
        @Parameter(description = "The namespace id") @PathVariable String namespace,
        @RequestBody(description = "The secret") @Valid @Body SecretRequest secret) throws IOException {
        return secretStoreService.put(tenantService.resolveTenant(), namespace, secret.key(), secret.value(), secret.description(), secret.tags());
    }

    @Patch(uri = "{namespace}/secrets/{key}")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Namespaces" }, summary = "Update the description and tags of a secret of a namespace")
    public ApiSecretMeta patchSecret(
        @Parameter(description = "The namespace id") @PathVariable String namespace,
        @Parameter(description = "The secret key") @PathVariable String key,
        @RequestBody(description = "The secret metadata") @Body SecretMetadataRequest secret) {
        return secretStoreService.patch(tenantService.resolveTenant(), namespace, key, secret.description(), secret.tags());
    }

    @Delete(uri = "{namespace}/secrets/{key}")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Namespaces" }, summary = "Delete a secret of a namespace")
    public HttpResponse<Void> deleteSecret(
        @Parameter(description = "The namespace id") @PathVariable String namespace,
        @Parameter(description = "The secret key") @PathVariable String key) throws IOException {
        secretStoreService.delete(tenantService.resolveTenant(), namespace, key);
        return HttpResponse.noContent();
    }

    public record SecretRequest(
        @NotBlank @Pattern(regexp = "[a-zA-Z0-9][a-zA-Z0-9_-]*") String key,
        @NotBlank String value,
        @Nullable String description,
        @Nullable List<PersistedSecretMetadata.Tag> tags) {
    }

    public record SecretMetadataRequest(
        @Nullable String description,
        @Nullable List<PersistedSecretMetadata.Tag> tags) {
    }
}

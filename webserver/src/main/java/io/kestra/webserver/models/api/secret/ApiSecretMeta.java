package io.kestra.webserver.models.api.secret;

import java.util.List;

import io.kestra.core.models.secret.PersistedSecretMetadata;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class ApiSecretMeta {
    private final String key;

    @Nullable
    private final String namespace;

    @Nullable
    private final String description;

    @Nullable
    private final List<PersistedSecretMetadata.Tag> tags;

    public ApiSecretMeta(
        @NotNull @Parameter(name = "key", description = "The key of secret.", required = true) String key,
        @Nullable @Parameter(name = "namespace", description = "The namespace of a stored secret; absent for an environment secret.") String namespace,
        @Nullable @Parameter(name = "description", description = "The description of the secret.") String description,
        @Nullable @Parameter(name = "tags", description = "The tags of the secret.") List<PersistedSecretMetadata.Tag> tags) {
        this.key = key;
        this.namespace = namespace;
        this.description = description;
        this.tags = tags;
    }

    public static ApiSecretMeta ofEnvironment(String key) {
        return new ApiSecretMeta(key, null, null, null);
    }

    public static ApiSecretMeta of(PersistedSecretMetadata metadata) {
        return new ApiSecretMeta(metadata.getName(), metadata.getNamespace(), metadata.getDescription(), metadata.getTags());
    }
}

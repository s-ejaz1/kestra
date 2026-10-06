package io.kestra.core.models.secret;

import java.time.Instant;
import java.util.List;

import io.kestra.core.models.HasUID;
import io.kestra.core.models.SoftDeletable;
import io.kestra.core.models.TenantInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.validations.TenantId;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.extern.jackson.Jacksonized;

/**
 * Metadata of a secret stored by Kestra. The encrypted value itself lives in the internal storage, so that a worker
 * can resolve it without reaching the database.
 */
@Builder(toBuilder = true)
@Jacksonized
@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
@EqualsAndHashCode
public class PersistedSecretMetadata implements SoftDeletable<PersistedSecretMetadata>, TenantInterface, HasUID {
    @With
    @Hidden
    @TenantId
    String tenantId;

    @NotNull
    String namespace;

    @NotNull
    String name;

    @Nullable
    String description;

    @Builder.Default
    List<Tag> tags = List.of();

    @Nullable
    Instant created;

    @Nullable
    Instant updated;

    boolean deleted;

    @Override
    public PersistedSecretMetadata toDeleted() {
        return this.toBuilder().updated(Instant.now()).deleted(true).build();
    }

    @Override
    public String uid() {
        return IdUtils.fromParts(getTenantId(), getNamespace(), getName());
    }

    public record Tag(String key, String value) {
    }
}

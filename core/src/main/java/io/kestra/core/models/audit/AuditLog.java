package io.kestra.core.models.audit;

import java.time.Instant;

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
 * A change made through the API: who did it, when, and on what.
 */
@Builder(toBuilder = true)
@Jacksonized
@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
@EqualsAndHashCode
public class AuditLog implements SoftDeletable<AuditLog>, TenantInterface, HasUID {
    @With
    @Hidden
    @TenantId
    String tenantId;

    @NotNull
    String id;

    @NotNull
    Instant date;

    @NotNull
    String userId;

    @NotNull
    String userEmail;

    /** The HTTP method of the change: POST, PUT, PATCH or DELETE. */
    @NotNull
    String method;

    @NotNull
    String path;

    /** The IAM permission of the resource changed, or {@code null} when the route maps to none. */
    @Nullable
    String resource;

    @Nullable
    String namespace;

    int status;

    boolean deleted;

    @Override
    public AuditLog toDeleted() {
        return this.toBuilder().deleted(true).build();
    }

    @Override
    public String uid() {
        return IdUtils.fromParts(getTenantId(), getId());
    }
}

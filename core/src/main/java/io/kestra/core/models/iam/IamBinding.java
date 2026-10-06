package io.kestra.core.models.iam;

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
 * Grants a role to a user, on the whole instance or on a namespace and all its children.
 */
@Builder(toBuilder = true)
@Jacksonized
@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
@EqualsAndHashCode
public class IamBinding implements SoftDeletable<IamBinding>, TenantInterface, HasUID {
    @With
    @Hidden
    @TenantId
    String tenantId;

    @NotNull
    String id;

    @NotNull
    String userId;

    @NotNull
    String roleId;

    /** {@code null} for a binding on the whole instance. */
    @Nullable
    String namespace;

    @Nullable
    Instant created;

    boolean deleted;

    public boolean appliesTo(@Nullable String namespace) {
        return this.namespace == null
            || (namespace != null && (namespace.equals(this.namespace) || namespace.startsWith(this.namespace + ".")));
    }

    @Override
    public IamBinding toDeleted() {
        return this.toBuilder().deleted(true).build();
    }

    @Override
    public String uid() {
        return IdUtils.fromParts(getTenantId(), getId());
    }
}

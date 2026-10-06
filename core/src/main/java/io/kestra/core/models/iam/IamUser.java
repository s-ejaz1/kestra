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
 * A user managed through IAM. The bcrypt hash is persisted, never the password, and is never exposed by the API.
 */
@Builder(toBuilder = true)
@Jacksonized
@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString(exclude = { "passwordSalt", "passwordHash" })
@EqualsAndHashCode
public class IamUser implements SoftDeletable<IamUser>, TenantInterface, HasUID {
    @With
    @Hidden
    @TenantId
    String tenantId;

    @NotNull
    String id;

    @NotNull
    String email;

    @Nullable
    String firstName;

    @Nullable
    String lastName;

    @NotNull
    String passwordSalt;

    @NotNull
    String passwordHash;

    boolean disabled;

    @Nullable
    Instant created;

    @Nullable
    Instant updated;

    boolean deleted;

    @Override
    public IamUser toDeleted() {
        return this.toBuilder().updated(Instant.now()).deleted(true).build();
    }

    @Override
    public String uid() {
        return IdUtils.fromParts(getTenantId(), getId());
    }
}

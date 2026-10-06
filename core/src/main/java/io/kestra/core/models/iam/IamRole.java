package io.kestra.core.models.iam;

import java.time.Instant;
import java.util.List;
import java.util.Map;

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

@Builder(toBuilder = true)
@Jacksonized
@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
@EqualsAndHashCode
public class IamRole implements SoftDeletable<IamRole>, TenantInterface, HasUID {
    @With
    @Hidden
    @TenantId
    String tenantId;

    @NotNull
    String id;

    @NotNull
    String name;

    @Nullable
    String description;

    @Builder.Default
    Map<Permission, List<Action>> permissions = Map.of();

    /** Built-in roles are seeded at startup and cannot be edited or deleted. */
    boolean builtIn;

    @Nullable
    Instant created;

    @Nullable
    Instant updated;

    boolean deleted;

    public boolean allows(Permission permission, Action action) {
        return permissions.getOrDefault(permission, List.of()).contains(action);
    }

    @Override
    public IamRole toDeleted() {
        return this.toBuilder().updated(Instant.now()).deleted(true).build();
    }

    @Override
    public String uid() {
        return IdUtils.fromParts(getTenantId(), getId());
    }
}

package io.kestra.core.models.assets;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.kestra.core.models.HasUID;
import io.kestra.core.models.SoftDeletable;
import io.kestra.core.models.TenantInterface;
import io.kestra.core.models.flows.State;
import io.kestra.core.utils.Enums;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.validations.TenantId;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.extern.jackson.Jacksonized;

/**
 * A task run that read or wrote an asset.
 */
@Builder(toBuilder = true)
@Jacksonized
@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
@EqualsAndHashCode
public class AssetUsage implements SoftDeletable<AssetUsage>, TenantInterface, HasUID {
    @Hidden
    @TenantId
    String tenantId;

    @NotNull
    String assetId;

    @NotNull
    Direction direction;

    String namespace;

    String flowId;

    @Nullable
    Integer flowRevision;

    String executionId;

    String taskId;

    String taskRunId;

    @Nullable
    State.Type state;

    @NotNull
    Instant date;

    boolean deleted;

    @Override
    public AssetUsage toDeleted() {
        return this.toBuilder().deleted(true).build();
    }

    @Override
    public String uid() {
        return IdUtils.fromParts(tenantId, taskRunId, direction.name(), assetId);
    }

    public enum Direction {
        INPUT,
        OUTPUT,
        UNKNOWN;

        @JsonCreator
        public static Direction fromString(final String value) {
            return Enums.getForNameIgnoreCase(value, Direction.class, UNKNOWN);
        }
    }
}

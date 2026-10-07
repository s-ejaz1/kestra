package io.kestra.core.models.assets;

import java.time.Instant;

import io.kestra.core.models.HasUID;
import io.kestra.core.models.SoftDeletable;
import io.kestra.core.models.TenantInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.validations.TenantId;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.extern.jackson.Jacksonized;

/**
 * An asset derived from another one: the last task run that read {@code sourceId} and wrote {@code targetId}.
 */
@Builder(toBuilder = true)
@Jacksonized
@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
@EqualsAndHashCode
public class AssetLineageEdge implements SoftDeletable<AssetLineageEdge>, TenantInterface, HasUID {
    @Hidden
    @TenantId
    String tenantId;

    @NotNull
    String sourceId;

    @NotNull
    String targetId;

    String namespace;

    String flowId;

    String taskId;

    String executionId;

    @NotNull
    Instant date;

    boolean deleted;

    @Override
    public AssetLineageEdge toDeleted() {
        return this.toBuilder().deleted(true).build();
    }

    @Override
    public String uid() {
        return IdUtils.fromParts(tenantId, sourceId, targetId);
    }
}

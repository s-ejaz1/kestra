package io.kestra.core.models.apps;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.kestra.core.models.HasUID;
import io.kestra.core.models.SoftDeletable;
import io.kestra.core.models.TenantInterface;
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
 * A form-like UI on top of a flow, declared in YAML: for each stage of the execution it starts, the blocks to show.
 * The blocks are rendered by the UI; the backend only validates their type.
 */
@Builder(toBuilder = true)
@Jacksonized
@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
@EqualsAndHashCode
public class App implements SoftDeletable<App>, TenantInterface, HasUID {
    public static final List<String> BLOCK_TYPES = List.of(
        "Markdown", "Alert", "CreateExecutionForm", "CreateExecutionButton", "Loading", "Logs", "Outputs"
    );

    @With
    @Hidden
    @TenantId
    String tenantId;

    @NotNull
    String id;

    @NotNull
    String namespace;

    @Nullable
    String displayName;

    @Nullable
    String description;

    @NotNull
    String flowId;

    @Builder.Default
    List<Layout> layout = List.of();

    /** The YAML the app was declared with, returned as-is so that comments and formatting survive an edit. */
    @NotNull
    String source;

    @Nullable
    Instant created;

    @Nullable
    Instant updated;

    boolean deleted;

    @Override
    public App toDeleted() {
        return this.toBuilder().updated(Instant.now()).deleted(true).build();
    }

    @Override
    public String uid() {
        return IdUtils.fromParts(getTenantId(), getNamespace(), getId());
    }

    public record Layout(Stage on, List<Map<String, Object>> blocks) {
    }

    /** The stage of the app's execution a layout applies to. */
    public enum Stage {
        OPEN,
        RUNNING,
        SUCCESS,
        FAILURE,
        UNKNOWN;

        @JsonCreator
        public static Stage fromString(final String value) {
            return Enums.getForNameIgnoreCase(value, Stage.class, UNKNOWN);
        }
    }
}

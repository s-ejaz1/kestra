package io.kestra.core.models.cases;

import java.time.Instant;

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
 * One entry of a case's timeline: a comment, or a change and who made it.
 */
@Builder(toBuilder = true)
@Jacksonized
@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
@EqualsAndHashCode
public class CaseActivity implements SoftDeletable<CaseActivity>, TenantInterface, HasUID {
    /** The author of the activities Kestra records on its own, such as an execution linked automatically. */
    public static final String SYSTEM_AUTHOR = "system";

    @Hidden
    @TenantId
    String tenantId;

    @NotNull
    String id;

    @NotNull
    String caseId;

    @NotNull
    Type type;

    @NotNull
    String author;

    @NotNull
    Instant date;

    /** The comment, or what the change was about, such as the execution or asset linked. */
    @Nullable
    String message;

    @Nullable
    String from;

    @Nullable
    String to;

    boolean deleted;

    @Override
    public CaseActivity toDeleted() {
        return this.toBuilder().deleted(true).build();
    }

    @Override
    public String uid() {
        return IdUtils.fromParts(getTenantId(), getId());
    }

    public enum Type {
        CREATED,
        COMMENT,
        STATUS_CHANGED,
        SEVERITY_CHANGED,
        ASSIGNEES_CHANGED,
        DUE_DATE_CHANGED,
        UPDATED,
        EXECUTION_LINKED,
        EXECUTION_UNLINKED,
        ASSET_LINKED,
        ASSET_UNLINKED,
        UNKNOWN;

        @JsonCreator
        public static Type fromString(final String value) {
            return Enums.getForNameIgnoreCase(value, Type.class, UNKNOWN);
        }
    }
}

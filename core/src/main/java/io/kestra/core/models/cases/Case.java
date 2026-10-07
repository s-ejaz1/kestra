package io.kestra.core.models.cases;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;

import io.kestra.core.models.HasUID;
import io.kestra.core.models.SoftDeletable;
import io.kestra.core.models.TenantInterface;
import io.kestra.core.models.flows.State;
import io.kestra.core.utils.Enums;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.validations.TenantId;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.extern.jackson.Jacksonized;

/**
 * An incident being triaged: who owns it, how urgent it is, by when it must be resolved, and the executions and assets involved.
 */
@Builder(toBuilder = true)
@Jacksonized
@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
@EqualsAndHashCode
public class Case implements SoftDeletable<Case>, TenantInterface, HasUID {
    @Hidden
    @TenantId
    String tenantId;

    @NotNull
    String id;

    @NotBlank
    String title;

    @Nullable
    String description;

    @NotNull
    Status status;

    @NotNull
    Severity severity;

    /** The namespace the case belongs to, which decides who can see it; {@code null} for a case visible instance-wide. */
    @Nullable
    String namespace;

    /** The flow whose failures the case is about. */
    @Nullable
    String flowId;

    /** Whether the next failed executions of {@link #flowId} are linked to this case while it is open. */
    boolean autoLink;

    @Builder.Default
    List<String> assignees = List.of();

    @Builder.Default
    List<String> labels = List.of();

    @Builder.Default
    List<LinkedExecution> executions = List.of();

    @Builder.Default
    List<String> assets = List.of();

    /** The SLA: when the case must be resolved by. */
    @Nullable
    Instant dueDate;

    @Nullable
    String createdBy;

    @NotNull
    Instant created;

    @NotNull
    Instant updated;

    @Nullable
    Instant acknowledgedDate;

    @Nullable
    Instant resolvedDate;

    boolean deleted;

    @Override
    public Case toDeleted() {
        return this.toBuilder().updated(Instant.now()).deleted(true).build();
    }

    @Override
    public String uid() {
        return IdUtils.fromParts(getTenantId(), getId());
    }

    @JsonIgnore
    public boolean isOpen() {
        return Status.OPEN == status || Status.ACKNOWLEDGED == status;
    }

    public record LinkedExecution(String executionId, String namespace, String flowId, @Nullable State.Type state, Instant linked) {
    }

    public enum Status {
        OPEN,
        ACKNOWLEDGED,
        RESOLVED,
        CLOSED,
        UNKNOWN;

        @JsonCreator
        public static Status fromString(final String value) {
            return Enums.getForNameIgnoreCase(value, Status.class, UNKNOWN);
        }
    }

    public enum Severity {
        CRITICAL(Duration.ofHours(4)),
        HIGH(Duration.ofDays(1)),
        MEDIUM(Duration.ofDays(3)),
        LOW(Duration.ofDays(7)),
        UNKNOWN(Duration.ofDays(7));

        /** The default time to resolve a case of this severity, used when no due date is given. */
        public final Duration sla;

        Severity(Duration sla) {
            this.sla = sla;
        }

        @JsonCreator
        public static Severity fromString(final String value) {
            return Enums.getForNameIgnoreCase(value, Severity.class, UNKNOWN);
        }
    }
}

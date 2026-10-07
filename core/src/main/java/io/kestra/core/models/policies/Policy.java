package io.kestra.core.models.policies;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

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
 * A governance policy: rules applied to every flow of a namespace (and its children), or of the whole tenant when it has no namespace.
 */
@Builder(toBuilder = true)
@Jacksonized
@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
@EqualsAndHashCode
public class Policy implements SoftDeletable<Policy>, TenantInterface, HasUID {
    @Hidden
    @TenantId
    String tenantId;

    @NotNull
    String id;

    /** The namespace the policy governs, children included; {@code null} for the whole tenant. */
    @Nullable
    String namespace;

    @Nullable
    String description;

    @NotNull
    Enforcement enforcement;

    @Builder.Default
    List<Rule> rules = List.of();

    /** The YAML the policy was declared with, returned as-is so that comments and formatting survive an edit. */
    @NotNull
    String source;

    @Nullable
    Instant created;

    @Nullable
    Instant updated;

    boolean deleted;

    @Override
    public Policy toDeleted() {
        return this.toBuilder().updated(Instant.now()).deleted(true).build();
    }

    @Override
    public String uid() {
        return IdUtils.fromParts(getTenantId(), getId());
    }

    public boolean appliesToNamespace(String flowNamespace) {
        return namespace == null || flowNamespace.equals(namespace) || flowNamespace.startsWith(namespace + ".");
    }

    public enum Enforcement {
        /** Applied to every flow in scope; a violation blocks saving and running the flow. */
        ENFORCE,
        /** Applied to every flow in scope, but a violation is only reported, to roll a policy out in stages. */
        AUDIT,
        /** Applied like {@link #ENFORCE}, only to the flows, tasks and triggers listing it in their {@code policyRefs}. */
        REFERENCE,
        DISABLED,
        UNKNOWN;

        @JsonCreator
        public static Enforcement fromString(final String value) {
            return Enums.getForNameIgnoreCase(value, Enforcement.class, UNKNOWN);
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = Defaults.class, name = "Defaults"),
        @JsonSubTypes.Type(value = Labels.class, name = "Labels"),
        @JsonSubTypes.Type(value = BlockPlugins.class, name = "BlockPlugins"),
        @JsonSubTypes.Type(value = PropertyBounds.class, name = "PropertyBounds"),
        @JsonSubTypes.Type(value = RequireLabels.class, name = "RequireLabels"),
    })
    public sealed interface Rule permits Defaults, Labels, BlockPlugins, PropertyBounds, RequireLabels {
    }

    /**
     * Default property values for the plugins whose type matches {@code target} ({@code *} as a trailing wildcard).
     *
     * @param override whether to replace a value the flow sets itself, rather than only fill in a missing one
     */
    public record Defaults(String target, Map<String, Object> values, boolean override) implements Rule {
    }

    /**
     * Labels added to the flow.
     *
     * @param override whether to replace the flow's own value and pin it, so that an execution cannot set it either
     */
    public record Labels(Map<String, String> labels, boolean override) implements Rule {
    }

    /** Plugin types the flow may not use ({@code *} as a trailing wildcard). */
    public record BlockPlugins(List<String> plugins) implements Rule {
    }

    /**
     * Bounds on a property of the plugins whose type matches {@code target}; a value written as an expression is not checked.
     *
     * @param property the property name, or a dotted path into it
     */
    public record PropertyBounds(
        String target,
        String property,
        boolean required,
        @Nullable List<String> allowedValues,
        @Nullable String pattern,
        @Nullable Double min,
        @Nullable Double max) implements Rule {
    }

    /**
     * Labels every flow must carry.
     *
     * @param values the values allowed for some of the keys
     */
    public record RequireLabels(List<String> keys, @Nullable Map<String, List<String>> values) implements Rule {
    }
}

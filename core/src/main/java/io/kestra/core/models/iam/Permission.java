package io.kestra.core.models.iam;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.kestra.core.utils.Enums;

/**
 * A resource a role can grant actions on. The names match the UI's {@code models/resource.ts}.
 */
public enum Permission {
    FLOW,
    EXECUTION,
    TRIGGER,
    NAMESPACE,
    KVSTORE,
    DASHBOARD,
    SECRET,
    COPILOT,
    MCP_SERVER,
    APP,
    TEST,
    ASSET,
    CASE,
    USER,
    ROLE,
    BINDING,
    UNKNOWN;

    @JsonCreator
    public static Permission fromString(final String value) {
        return Enums.getForNameIgnoreCase(value, Permission.class, UNKNOWN);
    }
}

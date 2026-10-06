package io.kestra.core.models.iam;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.kestra.core.utils.Enums;

/**
 * An action a role can grant on a {@link Permission}. The names match the UI's {@code models/action.ts}.
 */
public enum Action {
    VIEW,
    LIST,
    CREATE,
    UPDATE,
    DELETE,
    EXECUTE,
    RESTART,
    KILL,
    REPLAY,
    PAUSE,
    RESUME,
    UNQUEUE,
    FORCE_RUN,
    FOLLOW,
    CHANGE_LABELS,
    ACCESS_LOGS,
    ACCESS_OUTPUTS,
    ACCESS_FILES,
    EXPORT,
    IMPORT,
    DISABLE,
    ENABLE,
    VALIDATE,
    UNLOCK,
    BACKFILL,
    MANAGE_FILES,
    USE,
    UNKNOWN;

    @JsonCreator
    public static Action fromString(final String value) {
        return Enums.getForNameIgnoreCase(value, Action.class, UNKNOWN);
    }
}

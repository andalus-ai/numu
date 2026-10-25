package tech.kayys.numou.workspace.domain;

import java.util.Objects;

/** One actionable reason a saved view cannot be safely executed against the current table schema. */
public record DatabaseViewValidationIssue(Code code, String propertyKey, String message) {
    public enum Code {
        TABLE_MISSING,
        FILTER_PROPERTY_MISSING,
        FILTER_VALUE_INVALID,
        SORT_PROPERTY_MISSING,
        VISIBLE_PROPERTY_MISSING,
        GROUP_PROPERTY_MISSING,
        GROUP_PROPERTY_NOT_SELECT,
        GROUP_REQUIRES_BOARD
    }

    public DatabaseViewValidationIssue {
        Objects.requireNonNull(code, "code");
        if (message == null || message.isBlank()) throw new IllegalArgumentException("message cannot be blank");
        message = message.trim();
    }
}

package tech.kayys.numou.workspace.domain;

import java.util.Objects;

/** Conservative, user-reviewable repair guidance; suggestions never mutate a saved view themselves. */
public record DatabaseViewRepairSuggestion(
        DatabaseViewValidationIssue.Code issueCode,
        String propertyKey,
        Action action,
        String explanation) {
    public enum Action {
        REMOVE_FILTER,
        REPLACE_FILTER_VALUE,
        REMOVE_SORT,
        REMOVE_VISIBLE_PROPERTY,
        CLEAR_GROUPING,
        CHANGE_GROUPING_PROPERTY,
        REPAIR_TABLE_REFERENCE
    }

    public DatabaseViewRepairSuggestion {
        Objects.requireNonNull(issueCode, "issueCode");
        Objects.requireNonNull(action, "action");
        if (explanation == null || explanation.isBlank()) {
            throw new IllegalArgumentException("explanation cannot be blank");
        }
        explanation = explanation.trim();
    }
}

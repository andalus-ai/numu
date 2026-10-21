package tech.kayys.numou.planning.domain;

import java.util.Objects;

/**
 * Typed external identity for the work represented by an activity.
 * Examples include a scope node, backlog item, deliverable, or procurement package.
 * The planning core deliberately does not depend on any one work-item module.
 */
public record WorkItemReference(String type, String id) {
    public WorkItemReference {
        type = requireText(type, "Work item type");
        id = requireText(id, "Work item id");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value.trim();
    }
}

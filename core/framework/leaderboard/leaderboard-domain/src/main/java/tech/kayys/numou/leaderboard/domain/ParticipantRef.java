package tech.kayys.numou.leaderboard.domain;

import java.util.Objects;

/**
 * Opaque reference to a participant owned by another capability.
 * This type deliberately does not model people, employees, drivers or teams.
 */
public record ParticipantRef(String subjectType, String subjectId) {
    public ParticipantRef {
        subjectType = requireText(subjectType, "subjectType");
        subjectId = requireText(subjectId, "subjectId");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > (field.equals("subjectType") ? 120 : 200)) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
package tech.kayys.numou.leaderboard.domain;

import java.util.Objects;

/** Opaque pointer to the source capability record/event; never a copied source entity. */
public record SourceReference(String sourceType, String sourceId) {
    public SourceReference {
        sourceType = requireText(sourceType, "sourceType");
        sourceId = requireText(sourceId, "sourceId");
    }
    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return normalized;
    }
}
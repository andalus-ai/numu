package tech.kayys.numou.feedback.domain.rating;

import java.util.Locale;
import java.util.Objects;

/** Opaque reference to the rated entity; ownership remains with its source capability. */
public record RatingTarget(String targetType, String targetId, String branchId) {
    public RatingTarget {
        Objects.requireNonNull(targetType, "targetType cannot be null");
        Objects.requireNonNull(targetId, "targetId cannot be null");
        targetType = targetType.trim().toLowerCase(Locale.ROOT);
        targetId = targetId.trim();
        branchId = normalize(branchId);
        if (!targetType.matches("[a-z][a-z0-9._-]{1,63}")) throw new IllegalArgumentException("Invalid targetType");
        if (targetId.isEmpty() || targetId.length() > 200) throw new IllegalArgumentException("targetId must contain 1..200 characters");
        if (branchId != null && branchId.length() > 200) throw new IllegalArgumentException("branchId exceeds 200 characters");
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
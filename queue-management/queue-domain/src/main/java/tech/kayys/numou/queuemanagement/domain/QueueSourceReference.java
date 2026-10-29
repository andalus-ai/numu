package tech.kayys.numou.queuemanagement.domain;

/**
 * Stable reference to a source-owned record. Queue Management stores no copy of
 * the source record's business lifecycle or private persistence representation.
 */
public record QueueSourceReference(String capability, String resourceType, String resourceId) {
    public QueueSourceReference {
        capability = required(capability, "capability", 80);
        resourceType = required(resourceType, "resourceType", 80);
        resourceId = required(resourceId, "resourceId", 200);
    }

    private static String required(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " exceeds " + maxLength + " characters");
        }
        return normalized;
    }
}

package tech.kayys.numou.reservation.domain;

import java.util.Objects;
import java.util.Set;

/**
 * Definition of a bookable unit (room, dining table, conference desk, clinic bay, tennis court, vehicle).
 *
 * @param id             unique resource identifier
 * @param label          human-readable identifier / designation (e.g. "Table 4", "Suite 201", "Bay A")
 * @param minCapacity    minimum capacity accommodated
 * @param maxCapacity    maximum capacity accommodated
 * @param combinableWith other resources that can be physically or logically joined together
 */
public record BookableResource(
        ResourceId id,
        String label,
        int minCapacity,
        int maxCapacity,
        Set<ResourceId> combinableWith,
        Set<String> tags
) {
    public BookableResource {
        Objects.requireNonNull(id, "ResourceId cannot be null");
        Objects.requireNonNull(label, "label cannot be null");
        if (minCapacity < 1 || maxCapacity < minCapacity) {
            throw new IllegalArgumentException("Resource capacity must satisfy 1 <= min <= max");
        }
        combinableWith = combinableWith == null ? Set.of() : Set.copyOf(combinableWith);
        tags = tags == null ? Set.of() : tags.stream().filter(Objects::nonNull).map(String::trim)
                .filter(tag -> !tag.isEmpty()).map(String::toLowerCase).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /** Backward-compatible constructor for resources without explicit capability tags. */
    public BookableResource(ResourceId id, String label, int minCapacity, int maxCapacity,
                            Set<ResourceId> combinableWith) {
        this(id, label, minCapacity, maxCapacity, combinableWith, Set.of());
    }

    public boolean hasAllTags(Set<String> requiredTags) {
        if (requiredTags == null || requiredTags.isEmpty()) return true;
        var normalized = requiredTags.stream().filter(Objects::nonNull).map(String::trim)
                .filter(tag -> !tag.isEmpty()).map(String::toLowerCase).collect(java.util.stream.Collectors.toSet());
        return tags.containsAll(normalized);
    }

    public boolean canJoin(BookableResource other) {
        return combinableWith.contains(other.id()) || other.combinableWith.contains(id);
    }

    // Convenience compatibility accessors for restaurant / dining contexts
    public int minCovers() {
        return minCapacity;
    }

    public int maxCovers() {
        return maxCapacity;
    }
}

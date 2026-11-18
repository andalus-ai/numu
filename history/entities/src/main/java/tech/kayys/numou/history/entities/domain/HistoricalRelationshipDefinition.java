package tech.kayys.numou.history.entities.domain;

import java.util.Objects;
import java.util.Set;

/**
 * Vocabulary metadata that defines endpoint constraints and graph semantics.
 * Direction is explicit: symmetric relationships are not represented by two rows.
 */
public record HistoricalRelationshipDefinition(
        HistoricalRelationshipKindCode code,
        Direction direction,
        Set<HistoricalEntityKind> subjectKinds,
        Set<HistoricalEntityKind> objectKinds,
        boolean transitive,
        HistoricalRelationshipKindCode inverseCode) {

    public enum Direction { DIRECTED, SYMMETRIC }

    public HistoricalRelationshipDefinition {
        Objects.requireNonNull(code, "Relationship code is required");
        Objects.requireNonNull(direction, "Relationship direction is required");
        subjectKinds = Set.copyOf(Objects.requireNonNull(subjectKinds, "Subject kinds are required"));
        objectKinds = Set.copyOf(Objects.requireNonNull(objectKinds, "Object kinds are required"));
        if (subjectKinds.isEmpty() || objectKinds.isEmpty()) {
            throw new IllegalArgumentException("Relationship endpoint kind sets cannot be empty");
        }
        if (direction == Direction.SYMMETRIC) {
            if (!subjectKinds.equals(objectKinds)) {
                throw new IllegalArgumentException("Symmetric relationships must allow the same endpoint kinds");
            }
            if (inverseCode != null) {
                throw new IllegalArgumentException("Symmetric relationships do not declare an inverse code");
            }
            if (transitive) {
                throw new IllegalArgumentException("Symmetric does not imply transitive; use a separately defined transitive relation");
            }
        }
        if (code.equals(inverseCode)) {
            throw new IllegalArgumentException("A relationship cannot be its own inverse code");
        }
    }

    public void validateEndpoints(HistoricalEntityReference subject, HistoricalEntityReference object) {
        Objects.requireNonNull(subject, "Relationship subject is required");
        Objects.requireNonNull(object, "Relationship object is required");
        if (!subjectKinds.contains(subject.kind()) || !objectKinds.contains(object.kind())) {
            throw new IllegalArgumentException("Endpoint kinds are not allowed for relationship " + code.value());
        }
        if (direction == Direction.SYMMETRIC && subject.compareTo(object) > 0) {
            throw new IllegalArgumentException("Symmetric relationship endpoints must be in canonical order");
        }
    }
}

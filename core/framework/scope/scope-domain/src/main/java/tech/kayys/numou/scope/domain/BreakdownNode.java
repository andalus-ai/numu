package tech.kayys.numou.scope.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable node value/entity for a breakdown tree.
 *
 * Parent existence, same-structure membership, sibling-code uniqueness and
 * cycle prevention require a complete repository view and must be enforced
 * by the application/repository boundary rather than guessed locally here.
 */
public record BreakdownNode(
        BreakdownNodeId id,
        BreakdownStructureId structureId,
        BreakdownNodeId parentId,
        String code,
        String name,
        String kind,
        int position) {

    public BreakdownNode {
        Objects.requireNonNull(id, "Breakdown node id cannot be null");
        Objects.requireNonNull(structureId, "Breakdown structure id cannot be null");
        code = requireText(code, "Breakdown node code");
        name = requireText(name, "Breakdown node name");
        kind = normalizeKind(kind);
        if (id.equals(parentId)) {
            throw new IllegalArgumentException("A breakdown node cannot be its own parent");
        }
        if (position < 0) {
            throw new IllegalArgumentException("Breakdown node position cannot be negative");
        }
    }

    public static BreakdownNode create(
            BreakdownStructureId structureId,
            BreakdownNodeId parentId,
            String code,
            String name,
            String kind,
            int position) {
        return new BreakdownNode(
                BreakdownNodeId.generate(), structureId, parentId, code, name, kind, position);
    }

    public boolean isRoot() {
        return parentId == null;
    }

    public Optional<BreakdownNodeId> parent() {
        return Optional.ofNullable(parentId);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value.trim();
    }

    private static String normalizeKind(String value) {
        return requireText(value, "Breakdown node kind").toUpperCase(java.util.Locale.ROOT);
    }
}

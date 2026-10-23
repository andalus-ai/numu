package tech.kayys.numou.planning.domain;

import java.util.Objects;
import java.util.UUID;

/** Neutral WBS node; domain-specific meaning lives outside the planning core. */
public record WorkBreakdownNode(
        UUID id,
        UUID parentId,
        String code,
        String name,
        int position) {
    public WorkBreakdownNode {
        Objects.requireNonNull(id, "Node id cannot be null");
        code = requireText(code, "Node code");
        name = requireText(name, "Node name");
        if (id.equals(parentId)) throw new IllegalArgumentException("A node cannot be its own parent");
        if (position < 0) throw new IllegalArgumentException("Position cannot be negative");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " cannot be null");
        if (value.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
        return value.trim();
    }
}

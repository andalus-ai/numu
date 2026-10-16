package tech.kayys.syirkah.accounting.domain.workflow;

import java.util.Objects;

/** Stable identifier of a node within a {@link ProcessDefinition}. */
public record NodeId(String value) {
    public NodeId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) throw new IllegalArgumentException("NodeId must not be blank");
    }
    public static NodeId of(String v) { return new NodeId(v); }
}

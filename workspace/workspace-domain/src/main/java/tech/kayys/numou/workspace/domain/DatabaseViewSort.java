package tech.kayys.numou.workspace.domain;

import java.util.Objects;

/** Sort by a stable property key; direction is never supplied as SQL. */
public record DatabaseViewSort(String propertyKey, Direction direction) {
    public DatabaseViewSort {
        if (propertyKey == null || !propertyKey.matches("[a-z][a-z0-9_]{0,62}")) {
            throw new IllegalArgumentException("invalid sort property key");
        }
        Objects.requireNonNull(direction, "direction");
    }
    public enum Direction { ASC, DESC }
}

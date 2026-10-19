package tech.kayys.numou.workforce.domain.shared;

import tech.kayys.numou.workforce.domain.position.PositionId;
import java.util.Objects;

/**
 * Lightweight reference to a Position across domain boundaries (C-01 §5).
 */
public record PositionRef(PositionId positionId) {
    public PositionRef {
        Objects.requireNonNull(positionId, "positionId must not be null");
    }
}

package tech.kayys.syirkah.accounting.domain.hardening;

import java.time.Instant;
import java.util.Objects;

public record BridgeDispatch(
        String dispatchId,
        String sourceEventId,
        String bridgeCode,
        String targetCommand,
        Instant dispatchedAt
) {
    public BridgeDispatch {
        Objects.requireNonNull(dispatchId, "dispatchId must not be null");
        Objects.requireNonNull(sourceEventId, "sourceEventId must not be null");
        Objects.requireNonNull(bridgeCode, "bridgeCode must not be null");
        Objects.requireNonNull(targetCommand, "targetCommand must not be null");
        dispatchedAt = Objects.requireNonNullElse(dispatchedAt, Instant.now());
    }
}

package tech.kayys.numou.planning.domain;

import java.util.Objects;
import java.util.UUID;

/** A deterministic, machine-readable governance finding for forecast evidence. */
public record ForecastGovernanceFinding(UUID nodeId, Code code, Severity severity, String detail) {
    public ForecastGovernanceFinding {
        Objects.requireNonNull(nodeId, "Node id cannot be null");
        Objects.requireNonNull(code, "Finding code cannot be null");
        Objects.requireNonNull(severity, "Severity cannot be null");
        Objects.requireNonNull(detail, "Detail cannot be null");
        detail = detail.trim();
        if (detail.isEmpty()) throw new IllegalArgumentException("Finding detail cannot be blank");
    }
    public enum Severity { INFO, WARNING, ERROR }
    public enum Code {
        SCOPE_MISMATCH, DUPLICATE_WBS_NODE, MISSING_PROGRESS_EVIDENCE,
        FUTURE_PROGRESS_EVIDENCE, STALE_PROGRESS_EVIDENCE, UNSUPPORTED_FORECAST_METHOD
    }
}

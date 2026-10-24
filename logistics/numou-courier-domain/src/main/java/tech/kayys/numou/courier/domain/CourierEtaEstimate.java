package tech.kayys.numou.courier.domain;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Auditable ETA prediction snapshot; it does not overwrite a shipment's contractual promise. */
public record CourierEtaEstimate(UUID id, TenantId tenantId, ShipmentId shipmentId,
                                 Instant estimatedEarliestAt, Instant estimatedLatestAt,
                                 Instant calculatedAt, BigDecimal confidence,
                                 String modelVersion, Source source, String explanationCode) {
    public enum Source { RULES, HISTORICAL_MODEL, LIVE_TRAFFIC, MANUAL_OVERRIDE }
    public CourierEtaEstimate {
        Objects.requireNonNull(id); Objects.requireNonNull(tenantId); Objects.requireNonNull(shipmentId);
        Objects.requireNonNull(estimatedEarliestAt); Objects.requireNonNull(estimatedLatestAt);
        Objects.requireNonNull(calculatedAt); Objects.requireNonNull(confidence);
        modelVersion = required(modelVersion, "modelVersion");
        Objects.requireNonNull(source); explanationCode = required(explanationCode, "explanationCode");
        if (estimatedLatestAt.isBefore(estimatedEarliestAt)) throw new IllegalArgumentException("ETA window is inverted");
        if (confidence.signum() < 0 || confidence.compareTo(BigDecimal.ONE) > 0)
            throw new IllegalArgumentException("confidence must be between 0 and 1");
        if (estimatedLatestAt.isBefore(calculatedAt))
            throw new IllegalArgumentException("ETA window cannot end before calculation time");
    }
    private static String required(String v, String field) {
        Objects.requireNonNull(v, field); String s=v.trim();
        if (s.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return s;
    }
}
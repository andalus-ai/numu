package tech.kayys.numou.courier.domain;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Versioned lane/service agreement between a tenant and a network partner. */
public record CourierPartnerAgreement(UUID id, TenantId tenantId, UUID partnerId, String version,
                                      ServiceLevel serviceLevel, String originZone, String destinationZone,
                                      Instant effectiveFrom, Instant effectiveUntil, Status status) {
    public enum Status { DRAFT, ACTIVE, SUSPENDED, EXPIRED }
    public CourierPartnerAgreement {
        Objects.requireNonNull(id); Objects.requireNonNull(tenantId); Objects.requireNonNull(partnerId);
        version = required(version, "version"); Objects.requireNonNull(serviceLevel);
        originZone = zone(originZone); destinationZone = zone(destinationZone);
        Objects.requireNonNull(effectiveFrom); Objects.requireNonNull(status);
        if (effectiveUntil != null && !effectiveUntil.isAfter(effectiveFrom))
            throw new IllegalArgumentException("effectiveUntil must be after effectiveFrom");
    }
    public boolean usableAt(Instant at) {
        Objects.requireNonNull(at);
        return status == Status.ACTIVE && !at.isBefore(effectiveFrom)
                && (effectiveUntil == null || at.isBefore(effectiveUntil));
    }
    private static String zone(String v) { return required(v, "zone").toUpperCase(java.util.Locale.ROOT); }
    private static String required(String v, String field) {
        Objects.requireNonNull(v, field); String s=v.trim();
        if (s.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return s;
    }
}
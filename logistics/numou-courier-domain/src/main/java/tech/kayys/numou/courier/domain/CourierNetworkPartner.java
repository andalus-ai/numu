package tech.kayys.numou.courier.domain;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Tenant-owned directory entry for an external carrier, agent, franchisee, or delivery partner. */
public record CourierNetworkPartner(UUID id, TenantId tenantId, String partnerCode, String legalName,
                                    String countryCode, CourierNetworkPartnerStatus status,
                                    Instant createdAt, Instant updatedAt) {
    public CourierNetworkPartner {
        Objects.requireNonNull(id); Objects.requireNonNull(tenantId);
        partnerCode = required(partnerCode, "partnerCode").toUpperCase(java.util.Locale.ROOT);
        legalName = required(legalName, "legalName");
        countryCode = required(countryCode, "countryCode").toUpperCase(java.util.Locale.ROOT);
        if (!countryCode.matches("[A-Z]{2}")) throw new IllegalArgumentException("countryCode must be ISO alpha-2");
        Objects.requireNonNull(status); Objects.requireNonNull(createdAt); Objects.requireNonNull(updatedAt);
        if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("updatedAt cannot precede createdAt");
    }
    public boolean mayOperate() { return status == CourierNetworkPartnerStatus.ACTIVE; }
    private static String required(String v, String field) {
        Objects.requireNonNull(v, field); String s = v.trim();
        if (s.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return s;
    }
}
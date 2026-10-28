package tech.kayys.numou.affiliate.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Tenant-scoped partner profile; identity/contact verification belongs to host policy/adapters. */
public record AffiliatePartner(
        UUID id,
        String tenantId,
        String displayName,
        PartnerStatus status,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    public AffiliatePartner {
        Objects.requireNonNull(id, "id is required");
        tenantId = required(tenantId, "tenantId");
        displayName = required(displayName, "displayName");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
        if (version < 0) throw new IllegalArgumentException("version cannot be negative");
        if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("updatedAt cannot precede createdAt");
    }

    public static AffiliatePartner register(UUID id, String tenantId, String displayName, Instant now) {
        Objects.requireNonNull(now, "now is required");
        return new AffiliatePartner(id, tenantId, displayName, PartnerStatus.PENDING, now, now, 0);
    }

    public AffiliatePartner approve(Instant now) {
        if (status != PartnerStatus.PENDING) throw new IllegalStateException("Only pending partners can be approved");
        return transition(PartnerStatus.ACTIVE, now);
    }

    public AffiliatePartner suspend(Instant now) {
        if (status != PartnerStatus.ACTIVE) throw new IllegalStateException("Only active partners can be suspended");
        return transition(PartnerStatus.SUSPENDED, now);
    }

    public AffiliatePartner reinstate(Instant now) {
        if (status != PartnerStatus.SUSPENDED) throw new IllegalStateException("Only suspended partners can be reinstated");
        return transition(PartnerStatus.ACTIVE, now);
    }

    public AffiliatePartner terminate(Instant now) {
        if (status == PartnerStatus.TERMINATED) return this;
        return transition(PartnerStatus.TERMINATED, now);
    }

    private AffiliatePartner transition(PartnerStatus next, Instant now) {
        Objects.requireNonNull(now, "now is required");
        if (now.isBefore(updatedAt)) throw new IllegalArgumentException("now cannot precede updatedAt");
        return new AffiliatePartner(id, tenantId, displayName, next, createdAt, now, version + 1);
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        String normalized = value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
        return normalized;
    }
}

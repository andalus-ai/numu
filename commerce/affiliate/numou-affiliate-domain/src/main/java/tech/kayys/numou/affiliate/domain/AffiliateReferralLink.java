package tech.kayys.numou.affiliate.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Stable partner/program referral code. Attribution and destination URL policy are deliberately separate concerns.
 * Storage adapters must enforce code uniqueness within a tenant and use a non-enumerable public token when required.
 */
public record AffiliateReferralLink(
        UUID id,
        String tenantId,
        UUID programId,
        UUID partnerId,
        String code,
        ReferralLinkStatus status,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    private static final Pattern CODE = Pattern.compile("[a-z0-9][a-z0-9_-]{2,63}");

    public AffiliateReferralLink {
        Objects.requireNonNull(id, "id is required");
        tenantId = required(tenantId, "tenantId");
        Objects.requireNonNull(programId, "programId is required");
        Objects.requireNonNull(partnerId, "partnerId is required");
        code = normalizeCode(code);
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
        if (version < 0) throw new IllegalArgumentException("version cannot be negative");
        if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("updatedAt cannot precede createdAt");
    }

    public static AffiliateReferralLink issue(UUID id, String tenantId, UUID programId, UUID partnerId, String code, Instant now) {
        Objects.requireNonNull(now, "now is required");
        return new AffiliateReferralLink(id, tenantId, programId, partnerId, code, ReferralLinkStatus.ACTIVE, now, now, 0);
    }

    public AffiliateReferralLink disable(Instant now) {
        if (status == ReferralLinkStatus.DISABLED) return this;
        Objects.requireNonNull(now, "now is required");
        if (now.isBefore(updatedAt)) throw new IllegalArgumentException("now cannot precede updatedAt");
        return new AffiliateReferralLink(id, tenantId, programId, partnerId, code,
                ReferralLinkStatus.DISABLED, createdAt, now, version + 1);
    }

    public static String normalizeCode(String value) {
        Objects.requireNonNull(value, "code is required");
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!CODE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("code must be 3-64 characters: lowercase letters, digits, underscore, or hyphen");
        }
        return normalized;
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        String normalized = value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
        return normalized;
    }
}

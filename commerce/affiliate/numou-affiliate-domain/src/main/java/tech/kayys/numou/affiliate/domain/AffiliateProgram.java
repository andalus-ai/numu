package tech.kayys.numou.affiliate.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Tenant-scoped affiliate program. Lifecycle changes return a new snapshot;
 * persistence adapters must use optimistic concurrency for concurrent updates.
 */
public record AffiliateProgram(
        UUID id,
        String tenantId,
        String name,
        ProgramStatus status,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    public AffiliateProgram {
        Objects.requireNonNull(id, "id is required");
        tenantId = required(tenantId, "tenantId");
        name = required(name, "name");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
        if (version < 0) throw new IllegalArgumentException("version cannot be negative");
        if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("updatedAt cannot precede createdAt");
    }

    public static AffiliateProgram draft(UUID id, String tenantId, String name, Instant now) {
        Objects.requireNonNull(now, "now is required");
        return new AffiliateProgram(id, tenantId, name, ProgramStatus.DRAFT, now, now, 0);
    }

    public AffiliateProgram activate(Instant now) {
        if (status != ProgramStatus.DRAFT && status != ProgramStatus.SUSPENDED) {
            throw new IllegalStateException("Only draft or suspended programs can be activated");
        }
        return transition(ProgramStatus.ACTIVE, now);
    }

    public AffiliateProgram suspend(Instant now) {
        if (status != ProgramStatus.ACTIVE) {
            throw new IllegalStateException("Only active programs can be suspended");
        }
        return transition(ProgramStatus.SUSPENDED, now);
    }

    public AffiliateProgram archive(Instant now) {
        if (status == ProgramStatus.ARCHIVED) return this;
        if (status == ProgramStatus.ACTIVE) {
            throw new IllegalStateException("An active program must be suspended before archival");
        }
        return transition(ProgramStatus.ARCHIVED, now);
    }

    private AffiliateProgram transition(ProgramStatus next, Instant now) {
        Objects.requireNonNull(now, "now is required");
        if (now.isBefore(updatedAt)) throw new IllegalArgumentException("now cannot precede updatedAt");
        return new AffiliateProgram(id, tenantId, name, next, createdAt, now, version + 1);
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        String normalized = value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
        return normalized;
    }
}

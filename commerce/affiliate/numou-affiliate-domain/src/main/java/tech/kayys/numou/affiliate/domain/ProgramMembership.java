package tech.kayys.numou.affiliate.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Relationship between one tenant's program and partner; repositories enforce unique tenant/program/partner membership. */
public record ProgramMembership(
        UUID id,
        String tenantId,
        UUID programId,
        UUID partnerId,
        MembershipStatus status,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    public ProgramMembership {
        Objects.requireNonNull(id, "id is required");
        tenantId = required(tenantId, "tenantId");
        Objects.requireNonNull(programId, "programId is required");
        Objects.requireNonNull(partnerId, "partnerId is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
        if (version < 0) throw new IllegalArgumentException("version cannot be negative");
        if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("updatedAt cannot precede createdAt");
    }

    public static ProgramMembership invite(UUID id, String tenantId, UUID programId, UUID partnerId, Instant now) {
        Objects.requireNonNull(now, "now is required");
        return new ProgramMembership(id, tenantId, programId, partnerId, MembershipStatus.INVITED, now, now, 0);
    }

    public ProgramMembership accept(Instant now) {
        if (status != MembershipStatus.INVITED) throw new IllegalStateException("Only invited memberships can be accepted");
        return transition(MembershipStatus.ACTIVE, now);
    }

    public ProgramMembership suspend(Instant now) {
        if (status != MembershipStatus.ACTIVE) throw new IllegalStateException("Only active memberships can be suspended");
        return transition(MembershipStatus.SUSPENDED, now);
    }

    public ProgramMembership terminate(Instant now) {
        if (status == MembershipStatus.TERMINATED) return this;
        return transition(MembershipStatus.TERMINATED, now);
    }

    private ProgramMembership transition(MembershipStatus next, Instant now) {
        Objects.requireNonNull(now, "now is required");
        if (now.isBefore(updatedAt)) throw new IllegalArgumentException("now cannot precede updatedAt");
        return new ProgramMembership(id, tenantId, programId, partnerId, next, createdAt, now, version + 1);
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        String normalized = value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
        return normalized;
    }
}

package tech.kayys.numou.socialgraph.domain;

import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.Objects;

/**
 * Tenant-scoped bilateral social connection between two canonical Parties.
 * This is deliberately distinct from Ecosystem PartyRelationship, which
 * models commercial/organizational relationships.
 */
public final class SocialConnection {
    private final SocialConnectionId id;
    private final TenantId tenantId;
    private final PartyId requesterPartyId;
    private final PartyId recipientPartyId;
    private final SocialConnectionStatus status;
    private final Instant requestedAt;
    private final Instant updatedAt;
    private final Instant resolvedAt;
    private final int version;

    private SocialConnection(SocialConnectionId id, TenantId tenantId,
            PartyId requesterPartyId, PartyId recipientPartyId,
            SocialConnectionStatus status, Instant requestedAt,
            Instant updatedAt, Instant resolvedAt, int version) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId cannot be null");
        this.requesterPartyId = Objects.requireNonNull(requesterPartyId, "requesterPartyId cannot be null");
        this.recipientPartyId = Objects.requireNonNull(recipientPartyId, "recipientPartyId cannot be null");
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.requestedAt = Objects.requireNonNull(requestedAt, "requestedAt cannot be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt cannot be null");
        this.resolvedAt = resolvedAt;
        if (requesterPartyId.equals(recipientPartyId)) {
            throw new IllegalArgumentException("A Party cannot connect to itself");
        }
        if (version < 0) throw new IllegalArgumentException("version cannot be negative");
        if (updatedAt.isBefore(requestedAt)) throw new IllegalArgumentException("updatedAt precedes requestedAt");
        if (status == SocialConnectionStatus.PENDING && resolvedAt != null) {
            throw new IllegalArgumentException("Pending connection cannot have resolvedAt");
        }
        if (status != SocialConnectionStatus.PENDING && resolvedAt == null) {
            throw new IllegalArgumentException("Resolved connection must have resolvedAt");
        }
        this.version = version;
    }

    public static SocialConnection request(SocialConnectionId id, TenantId tenantId,
            PartyId requester, PartyId recipient, Instant now) {
        Objects.requireNonNull(now, "now cannot be null");
        return new SocialConnection(id, tenantId, requester, recipient,
                SocialConnectionStatus.PENDING, now, now, null, 0);
    }

    public static SocialConnection rehydrate(SocialConnectionId id, TenantId tenantId,
            PartyId requester, PartyId recipient, SocialConnectionStatus status,
            Instant requestedAt, Instant updatedAt, Instant resolvedAt, int version) {
        return new SocialConnection(id, tenantId, requester, recipient, status,
                requestedAt, updatedAt, resolvedAt, version);
    }

    public SocialConnection accept(PartyId actor, Instant now) {
        requirePending();
        requireActor(recipientPartyId, actor, "Only the recipient may accept a connection request");
        return transition(SocialConnectionStatus.ACTIVE, now);
    }

    public SocialConnection reject(PartyId actor, Instant now) {
        requirePending();
        requireActor(recipientPartyId, actor, "Only the recipient may reject a connection request");
        return transition(SocialConnectionStatus.REJECTED, now);
    }

    public SocialConnection cancel(PartyId actor, Instant now) {
        requirePending();
        requireActor(requesterPartyId, actor, "Only the requester may cancel a connection request");
        return transition(SocialConnectionStatus.CANCELLED, now);
    }

    public SocialConnection remove(PartyId actor, Instant now) {
        if (status != SocialConnectionStatus.ACTIVE) {
            throw new IllegalStateException("Only an active connection can be removed");
        }
        if (!requesterPartyId.equals(actor) && !recipientPartyId.equals(actor)) {
            throw new IllegalArgumentException("Actor is not a participant in this connection");
        }
        return transition(SocialConnectionStatus.REMOVED, now);
    }

    private SocialConnection transition(SocialConnectionStatus next, Instant now) {
        Objects.requireNonNull(now, "now cannot be null");
        if (now.isBefore(updatedAt)) throw new IllegalArgumentException("Transition time cannot move backwards");
        return new SocialConnection(id, tenantId, requesterPartyId, recipientPartyId,
                next, requestedAt, now, now, version + 1);
    }

    private void requirePending() {
        if (status != SocialConnectionStatus.PENDING) {
            throw new IllegalStateException("Connection request is not pending");
        }
    }

    private static void requireActor(PartyId expected, PartyId actor, String message) {
        if (!expected.equals(actor)) throw new IllegalArgumentException(message);
    }

    public SocialConnectionId id() { return id; }
    public TenantId tenantId() { return tenantId; }
    public PartyId requesterPartyId() { return requesterPartyId; }
    public PartyId recipientPartyId() { return recipientPartyId; }
    public SocialConnectionStatus status() { return status; }
    public Instant requestedAt() { return requestedAt; }
    public Instant updatedAt() { return updatedAt; }
    public Instant resolvedAt() { return resolvedAt; }
    public int version() { return version; }
}

package tech.kayys.syirkah.tenancy.domain.membership;

import tech.kayys.syirkah.foundation.domain.audit.AuditMeta;
import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.foundation.domain.ref.UserRef;
import tech.kayys.syirkah.tenancy.domain.membership.event.TenantMemberAccepted;
import tech.kayys.syirkah.tenancy.domain.membership.event.TenantMemberInvited;
import tech.kayys.syirkah.tenancy.domain.membership.event.TenantMemberRemoved;
import tech.kayys.syirkah.tenancy.domain.membership.event.TenantMemberSuspended;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * TenantMembership aggregate — a user's relationship with a specific Tenant.
 *
 * <p>Membership answers: "Is this identity a member of this tenant?"
 * Authorization answers: "What can this member do?" — that belongs elsewhere.
 *
 * <p>One user may hold memberships in many tenants simultaneously.
 * Membership is NOT the same as Employment (which lives in Workforce).
 */
public final class TenantMembership extends AbstractAggregateRoot<TenantMembershipId> {

    private final TenantId tenantId;
    private final UserRef user;
    private final TenantMemberType type;
    private TenantMemberStatus status;
    private final AuditMeta audit;

    private TenantMembership(TenantMembershipId id, TenantId tenantId, UserRef user,
                              TenantMemberType type, TenantMemberStatus status,
                              AuditMeta audit) {
        super(id);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.user     = Objects.requireNonNull(user);
        this.type     = Objects.requireNonNull(type);
        this.status   = Objects.requireNonNull(status);
        this.audit    = Objects.requireNonNull(audit);
    }

    /** Creates a membership in INVITED state. */
    public static TenantMembership invite(TenantMembershipId id, TenantId tenantId,
                                          UserRef user, TenantMemberType type,
                                          AuditMeta audit) {
        var m = new TenantMembership(id, tenantId, user, type,
                TenantMemberStatus.INVITED, audit);
        m.raise(new TenantMemberInvited(UUID.randomUUID(), Instant.now(), tenantId, id, user));
        return m;
    }

    /** Invitation accepted — transitions INVITED → ACTIVE. */
    public void accept() {
        if (status != TenantMemberStatus.INVITED) {
            throw new IllegalStateException("Membership cannot be accepted from " + status);
        }
        status = TenantMemberStatus.ACTIVE;
        incrementVersion();
        updatedAt = Instant.now();
        raise(new TenantMemberAccepted(UUID.randomUUID(), Instant.now(), tenantId, id(), user));
    }

    /** Transitions ACTIVE → SUSPENDED. */
    public void suspend() {
        if (status != TenantMemberStatus.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE membership can be suspended");
        }
        status = TenantMemberStatus.SUSPENDED;
        incrementVersion();
        updatedAt = Instant.now();
        raise(new TenantMemberSuspended(UUID.randomUUID(), Instant.now(), tenantId, id(), user));
    }

    /** Transitions SUSPENDED → ACTIVE. */
    public void activate() {
        if (status != TenantMemberStatus.SUSPENDED) {
            throw new IllegalStateException("Only SUSPENDED membership can be reactivated");
        }
        status = TenantMemberStatus.ACTIVE;
        incrementVersion();
        updatedAt = Instant.now();
    }

    /** Removes the member (idempotent if already REMOVED). */
    public void remove() {
        if (status == TenantMemberStatus.REMOVED) {
            return;
        }
        status = TenantMemberStatus.REMOVED;
        incrementVersion();
        updatedAt = Instant.now();
        raise(new TenantMemberRemoved(UUID.randomUUID(), Instant.now(), tenantId, id(), user));
    }

    // ── accessors ────────────────────────────────────────────────────────────

    public TenantId tenantId()            { return tenantId; }
    public UserRef user()                 { return user; }
    public TenantMemberType type()        { return type; }
    public TenantMemberStatus status()    { return status; }
    public AuditMeta audit()             { return audit; }
}

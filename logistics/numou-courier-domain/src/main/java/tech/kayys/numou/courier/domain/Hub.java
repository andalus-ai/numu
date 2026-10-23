package tech.kayys.numou.courier.domain;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.organization.domain.OrganizationId;
import tech.kayys.numou.organization.domain.OrganizationUnitId;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Objects;

/**
 * Tenant-owned operational location. A branch is an organizational unit;
 * a hub is a physical/operational facility and is therefore modeled separately.
 *
 * <p>Cross-tenant facility sharing is intentionally not implicit. A hub has one
 * owning tenant; future sharing must be represented by an explicit facility
 * agreement and authorization boundary rather than by weakening this invariant.
 */
public final class Hub {
    private final HubId id;
    private final TenantId tenantId;
    private final OrganizationId organizationId;
    private final OrganizationUnitId branchUnitId;
    private final String code;
    private String name;
    private final HubType type;
    private final HubAddress address;
    private final ZoneId timeZone;
    private HubStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    private Hub(HubId id, TenantId tenantId, OrganizationId organizationId,
                OrganizationUnitId branchUnitId, String code, String name,
                HubType type, HubAddress address, ZoneId timeZone, Instant now) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId must not be null");
        this.branchUnitId = Objects.requireNonNull(branchUnitId, "branchUnitId must not be null");
        this.code = required(code, "code").toUpperCase(Locale.ROOT);
        if (!this.code.matches("[A-Z0-9][A-Z0-9._-]{1,31}")) {
            throw new IllegalArgumentException("code must be 2-32 characters using letters, digits, '.', '_' or '-'");
        }
        this.name = required(name, "name");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.address = Objects.requireNonNull(address, "address must not be null");
        this.timeZone = Objects.requireNonNull(timeZone, "timeZone must not be null");
        this.status = HubStatus.DRAFT;
        this.createdAt = Objects.requireNonNull(now, "now must not be null");
        this.updatedAt = now;
    }

    public static Hub create(HubId id, TenantId tenantId, OrganizationId organizationId,
                             OrganizationUnitId branchUnitId, String code, String name,
                             HubType type, HubAddress address, ZoneId timeZone, Instant now) {
        return new Hub(id, tenantId, organizationId, branchUnitId, code, name,
                type, address, timeZone, now);
    }

    /** Reconstitutes a persisted hub without replaying lifecycle transitions. */
    public static Hub reconstitute(HubId id, TenantId tenantId, OrganizationId organizationId,
                                   OrganizationUnitId branchUnitId, String code, String name,
                                   HubType type, HubAddress address, ZoneId timeZone,
                                   HubStatus status, Instant createdAt, Instant updatedAt) {
        Hub hub = new Hub(id, tenantId, organizationId, branchUnitId, code, name,
                type, address, timeZone, createdAt);
        hub.status = Objects.requireNonNull(status, "status must not be null");
        hub.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
        return hub;
    }

    public void activate(Instant now) {
        transitionFrom(HubStatus.DRAFT, HubStatus.ACTIVE, now);
    }

    public void suspend(Instant now) {
        transitionFrom(HubStatus.ACTIVE, HubStatus.SUSPENDED, now);
    }

    public void resume(Instant now) {
        transitionFrom(HubStatus.SUSPENDED, HubStatus.ACTIVE, now);
    }

    public void close(Instant now) {
        if (status == HubStatus.CLOSED) {
            return;
        }
        if (status == HubStatus.DRAFT) {
            throw new IllegalStateException("a draft hub must be activated before it can be closed");
        }
        status = HubStatus.CLOSED;
        updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void rename(String name, Instant now) {
        if (status == HubStatus.CLOSED) {
            throw new IllegalStateException("a closed hub cannot be renamed");
        }
        this.name = required(name, "name");
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    private void transitionFrom(HubStatus expected, HubStatus target, Instant now) {
        if (status != expected) {
            throw new IllegalStateException("cannot transition hub from " + status + " to " + target);
        }
        status = target;
        updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    public HubId id() { return id; }
    public TenantId tenantId() { return tenantId; }
    public OrganizationId organizationId() { return organizationId; }
    public OrganizationUnitId branchUnitId() { return branchUnitId; }
    public String code() { return code; }
    public String name() { return name; }
    public HubType type() { return type; }
    public HubAddress address() { return address; }
    public ZoneId timeZone() { return timeZone; }
    public HubStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}

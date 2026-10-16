package tech.kayys.numou.ecosystem.domain.model;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.foundation.domain.exception.InvalidStateException;
import tech.kayys.numou.ecosystem.domain.identifier.CapabilityId;
import tech.kayys.numou.ecosystem.domain.valueobject.CapabilityCategory;
import tech.kayys.numou.ecosystem.domain.valueobject.CapabilityStatus;

import java.time.Instant;
import java.util.Objects;

/**
 * A capability that a participant can provide or consume
 * (Docs/Plan/base00.md §2/§14, base01.md §4).
 *
 * <p>Capabilities are deliberately technology-neutral. "Transportation" is
 * a capability; "Numou FMS" is one implementation of it, "DHL API" is
 * another, and a tenant's own TMS is a third. Ownership of the capability
 * definition and consumption of the capability are different concerns.
 */
public final class Capability extends AbstractAggregateRoot<CapabilityId> {

    private static final long serialVersionUID = 1L;

    private String code;
    private String name;
    private CapabilityCategory category;
    private CapabilityStatus status;

    private Capability() {
        super();
    }

    private Capability(CapabilityId id) {
        super(id);
        this.status = CapabilityStatus.DRAFT;
    }

    /**
     * Defines a new capability in the catalog.
     *
     * <p>The code is the stable, dotted contract other systems reference -
     * for example {@code logistics.transportation} or {@code finance.tax}.
     */
    public static Capability define(
            CapabilityId id,
            String code,
            String name,
            CapabilityCategory category) {

        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(category, "category cannot be null");

        final var capability = new Capability(id);
        capability.code = requireCode(code);
        capability.name = requireText(name, "Capability name");
        capability.category = category;
        return capability;
    }

    /** Rehydrates a capability from persistence. */
    public static Capability rehydrate(
            CapabilityId id,
            String code,
            String name,
            CapabilityCategory category,
            CapabilityStatus status) {

        final var capability = new Capability(id);
        capability.code = code;
        capability.name = name;
        capability.category = category;
        capability.status = status;
        return capability;
    }

    /** Publishes the capability so providers may declare it. */
    public void publish() {
        if (status == CapabilityStatus.RETIRED) {
            throw new InvalidStateException("A retired capability cannot be published");
        }
        this.status = CapabilityStatus.ACTIVE;
        touch();
    }

    /** Marks the capability as superseded but still honoured. */
    public void deprecate() {
        if (status != CapabilityStatus.ACTIVE) {
            throw new InvalidStateException(
                    "Only an active capability can be deprecated, was " + status
            );
        }
        this.status = CapabilityStatus.DEPRECATED;
        touch();
    }

    /** Withdraws the capability from the catalog. */
    public void retire() {
        if (status == CapabilityStatus.RETIRED) {
            throw new InvalidStateException("Capability is already retired");
        }
        this.status = CapabilityStatus.RETIRED;
        touch();
    }

    public boolean isConsumable() {
        return status == CapabilityStatus.ACTIVE;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public CapabilityCategory getCategory() {
        return category;
    }

    public CapabilityStatus getStatus() {
        return status;
    }

    private void touch() {
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    private static String requireCode(String code) {
        final var value = requireText(code, "Capability code");
        if (!value.matches("[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)+")) {
            throw new BusinessRuleViolation(
                    "Capability code must be dotted lower-case, e.g. logistics.transportation"
            );
        }
        return value;
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleViolation(label + " is required");
        }
        return value.trim();
    }

    @Override
    public String toString() {
        return "Capability{id=" + getId()
                + ", code='" + code + '\''
                + ", category=" + category
                + ", status=" + status
                + '}';
    }
}

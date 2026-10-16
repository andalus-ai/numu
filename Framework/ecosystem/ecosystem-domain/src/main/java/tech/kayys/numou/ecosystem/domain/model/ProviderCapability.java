package tech.kayys.syirkah.ecosystem.domain.model;

import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.syirkah.foundation.domain.exception.InvalidStateException;
import tech.kayys.syirkah.ecosystem.domain.identifier.CapabilityId;
import tech.kayys.syirkah.ecosystem.domain.identifier.ParticipantId;
import tech.kayys.syirkah.ecosystem.domain.identifier.ProviderCapabilityId;

import java.time.Instant;
import java.util.Objects;

/**
 * The claim "this participant can supply this capability"
 * (Docs/Plan/base00.md §2, base01.md §5 and §P1-08).
 *
 * <p>This is the join between ecosystem and provider, and it is what makes
 * provider-neutral fulfilment possible: a shipment can be offered to every
 * provider capability matching {@code logistics.transportation} regardless
 * of whether the provider is an internal fleet, Numou's own fleet, a 3PL,
 * a carrier, or an external logistics platform.
 */
public final class ProviderCapability extends AbstractAggregateRoot<ProviderCapabilityId> {

    private static final long serialVersionUID = 1L;

    private ParticipantId participantId;
    private CapabilityId capabilityId;

    /** How this provider implements the capability, e.g. INTERNAL, SYIRKAH, EXTERNAL. */
    private String provisioningModel;

    /** Provider-declared service area / scope, free-form (e.g. "ID-JB", "nationwide"). */
    private String coverage;

    private boolean active;

    private ProviderCapability() {
        super();
    }

    private ProviderCapability(ProviderCapabilityId id) {
        super(id);
        this.active = true;
    }

    /**
     * Declares that a participant provides a capability.
     */
    public static ProviderCapability declare(
            ProviderCapabilityId id,
            ParticipantId participantId,
            CapabilityId capabilityId,
            String provisioningModel,
            String coverage) {

        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(participantId, "participantId cannot be null");
        Objects.requireNonNull(capabilityId, "capabilityId cannot be null");

        final var declaration = new ProviderCapability(id);
        declaration.participantId = participantId;
        declaration.capabilityId = capabilityId;
        declaration.provisioningModel = requireText(provisioningModel, "Provisioning model");
        declaration.coverage = coverage == null ? "" : coverage.trim();
        return declaration;
    }

    /** Rehydrates a provider capability from persistence. */
    public static ProviderCapability rehydrate(
            ProviderCapabilityId id,
            ParticipantId participantId,
            CapabilityId capabilityId,
            String provisioningModel,
            String coverage,
            boolean active) {

        final var declaration = new ProviderCapability(id);
        declaration.participantId = participantId;
        declaration.capabilityId = capabilityId;
        declaration.provisioningModel = provisioningModel;
        declaration.coverage = coverage;
        declaration.active = active;
        return declaration;
    }

    /** Narrows or widens the declared coverage. */
    public void updateCoverage(String coverage) {
        this.coverage = coverage == null ? "" : coverage.trim();
        touch();
    }

    /** Withdraws the provider's capability claim. */
    public void withdraw() {
        if (!active) {
            throw new InvalidStateException("Provider capability is already withdrawn");
        }
        this.active = false;
        touch();
    }

    /** Reinstates a previously withdrawn capability claim. */
    public void reinstate() {
        if (active) {
            throw new InvalidStateException("Provider capability is already active");
        }
        this.active = true;
        touch();
    }

    public boolean canFulfil() {
        return active;
    }

    public ParticipantId getParticipantId() {
        return participantId;
    }

    public CapabilityId getCapabilityId() {
        return capabilityId;
    }

    public String getProvisioningModel() {
        return provisioningModel;
    }

    public String getCoverage() {
        return coverage;
    }

    public boolean isActive() {
        return active;
    }

    private void touch() {
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleViolation(label + " is required");
        }
        return value.trim();
    }

    @Override
    public String toString() {
        return "ProviderCapability{id=" + getId()
                + ", participantId=" + participantId
                + ", capabilityId=" + capabilityId
                + ", provisioningModel='" + provisioningModel + '\''
                + ", active=" + active
                + '}';
    }
}

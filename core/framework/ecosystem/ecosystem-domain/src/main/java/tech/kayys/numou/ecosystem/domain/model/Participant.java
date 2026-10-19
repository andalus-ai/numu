package tech.kayys.numou.ecosystem.domain.model;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.foundation.domain.exception.InvalidStateException;
import tech.kayys.numou.ecosystem.domain.event.ParticipantRegistered;
import tech.kayys.numou.ecosystem.domain.identifier.ParticipantId;
import tech.kayys.numou.ecosystem.domain.valueobject.ParticipantStatus;
import tech.kayys.numou.ecosystem.domain.valueobject.ParticipantType;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A participant in the Numou ecosystem (Docs/Plan/base00.md §3, base01.md §3).
 *
 * <p>A participant is <em>not</em> the same thing as its software. One
 * participant may use Numou ERP, own 100 trucks and provide logistics to
 * other tenants; another may run its own TMS and only integrate by API.
 * Both are first-class participants, and neither is modelled as a special
 * case of the other.
 *
 * <p>Aggregate root: identity, type and lifecycle of the participation
 * form the consistency boundary. Capability claims are separate aggregates
 * ({@link ProviderCapability}) referencing this participant by identity.
 */
public final class Participant extends AbstractAggregateRoot<ParticipantId> {

    private static final long serialVersionUID = 1L;

    private String code;
    private String name;
    private ParticipantType type;
    private ParticipantStatus status;

    /** Optional link to the Organization aggregate that owns this participation. */
    private UUID organizationId;

    /** Optional owning tenant - absent for external providers. */
    private UUID tenantId;

    private Participant() {
        super();
    }

    private Participant(ParticipantId id) {
        super(id);
        this.status = ParticipantStatus.PENDING;
    }

    /**
     * Registers a new participant in the ecosystem.
     *
     * <p>Always starts {@link ParticipantStatus#PENDING}: a participant may
     * exist in the ecosystem without yet being allowed to transact.
     */
    public static Participant register(
            ParticipantId id,
            String code,
            String name,
            ParticipantType type,
            UUID organizationId,
            UUID tenantId) {

        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(type, "type cannot be null");

        final var participant = new Participant(id);
        participant.code = requireText(code, "Participant code");
        participant.name = requireText(name, "Participant name");
        participant.type = type;
        participant.organizationId = organizationId;
        participant.tenantId = tenantId;

        participant.raise(ParticipantRegistered.of(
                id.value(),
                participant.code,
                participant.name,
                type.name()
        ));

        return participant;
    }

    /**
     * Rehydrates a participant from persistence.
     */
    public static Participant rehydrate(
            ParticipantId id,
            String code,
            String name,
            ParticipantType type,
            ParticipantStatus status,
            UUID organizationId,
            UUID tenantId) {

        final var participant = new Participant(id);
        participant.code = code;
        participant.name = name;
        participant.type = type;
        participant.status = status;
        participant.organizationId = organizationId;
        participant.tenantId = tenantId;
        return participant;
    }

    /** Verifies the participant and lets it transact in the ecosystem. */
    public void activate() {
        if (status == ParticipantStatus.RETIRED) {
            throw new InvalidStateException(
                    "A retired participant (" + code + ") cannot be reactivated"
            );
        }
        this.status = ParticipantStatus.ACTIVE;
        touch();
    }

    /** Temporarily blocks the participant without losing its history. */
    public void suspend(String reason) {
        if (status != ParticipantStatus.ACTIVE) {
            throw new InvalidStateException(
                    "Only an active participant can be suspended, was " + status
            );
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleViolation("A suspension reason is required");
        }
        this.status = ParticipantStatus.SUSPENDED;
        touch();
    }

    /**
     * Removes the participant from the ecosystem.
     *
     * <p>Never a delete: contracts and transactions referencing this
     * participant must remain auditable.
     */
    public void retire() {
        if (status == ParticipantStatus.RETIRED) {
            throw new InvalidStateException("Participant is already retired");
        }
        this.status = ParticipantStatus.RETIRED;
        touch();
    }

    /** Renames the participant (trading names change, identity does not). */
    public void rename(String newName) {
        this.name = requireText(newName, "Participant name");
        touch();
    }

    /** Links this participation to an owning tenant. */
    public void assignTenant(UUID tenantId) {
        if (this.type == ParticipantType.EXTERNAL_PROVIDER) {
            throw new BusinessRuleViolation(
                    "An external provider is not owned by a Numou tenant"
            );
        }
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId cannot be null");
        touch();
    }

    public boolean canTransact() {
        return status == ParticipantStatus.ACTIVE;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public ParticipantType getType() {
        return type;
    }

    public ParticipantStatus getStatus() {
        return status;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getTenantId() {
        return tenantId;
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
        return "Participant{id=" + getId()
                + ", code='" + code + '\''
                + ", name='" + name + '\''
                + ", type=" + type
                + ", status=" + status
                + '}';
    }
}

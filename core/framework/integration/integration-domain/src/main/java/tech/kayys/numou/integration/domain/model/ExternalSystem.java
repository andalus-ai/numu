package tech.kayys.numou.integration.domain.model;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.foundation.domain.exception.InvalidStateException;
import tech.kayys.numou.integration.domain.identifier.ExternalSystemId;
import tech.kayys.numou.integration.domain.valueobject.AuthScheme;
import tech.kayys.numou.integration.domain.valueobject.ExternalSystemStatus;
import tech.kayys.numou.integration.domain.valueobject.ExternalSystemType;
import tech.kayys.numou.integration.domain.valueobject.IntegrationDirection;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A partner system integrated with Numou (base01.md §13, §P1-16).
 *
 * <p>Modelling the partner's system as an aggregate is what lets PT ABC
 * keep running its own FMS while Numou consumes
 * {@code VehicleLocationUpdated} from it. The system carries its
 * endpoints, authentication scheme, direction of flow and the capability
 * codes it exposes - never its business data.
 */
public final class ExternalSystem extends AbstractAggregateRoot<ExternalSystemId> {

    private static final long serialVersionUID = 1L;

    private UUID participantId;
    private String name;
    private ExternalSystemType type;
    private IntegrationDirection direction;
    private AuthScheme authScheme;
    private ExternalSystemStatus status;
    private String baseUrl;
    private String credentialHandle;
    private final Set<String> capabilityCodes = new LinkedHashSet<>();

    private ExternalSystem() {
        super();
    }

    private ExternalSystem(ExternalSystemId id) {
        super(id);
        this.status = ExternalSystemStatus.PENDING;
    }

    /**
     * Registers a partner system.
     *
     * @param credentialHandle a reference resolved later by the credential
     *                         resolver port - secrets never enter the domain
     */
    public static ExternalSystem register(
            ExternalSystemId id,
            UUID participantId,
            String name,
            ExternalSystemType type,
            IntegrationDirection direction,
            AuthScheme authScheme,
            String baseUrl,
            String credentialHandle) {

        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(participantId, "participantId cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(direction, "direction cannot be null");
        Objects.requireNonNull(authScheme, "authScheme cannot be null");

        if (authScheme == AuthScheme.NONE && !isSandbox(baseUrl)) {
            throw new BusinessRuleViolation(
                    "Unauthenticated integrations are only allowed for sandbox endpoints");
        }
        if (authScheme != AuthScheme.NONE
                && (credentialHandle == null || credentialHandle.isBlank())) {
            throw new BusinessRuleViolation(
                    "An authentication scheme requires a credential handle");
        }

        final var system = new ExternalSystem(id);
        system.participantId = participantId;
        system.name = requireText(name, "External system name");
        system.type = type;
        system.direction = direction;
        system.authScheme = authScheme;
        system.baseUrl = requireText(baseUrl, "Base URL");
        system.credentialHandle = credentialHandle == null ? "" : credentialHandle.trim();
        return system;
    }

    /** Rehydrates from persistence. */
    public static ExternalSystem rehydrate(
            ExternalSystemId id,
            UUID participantId,
            String name,
            ExternalSystemType type,
            IntegrationDirection direction,
            AuthScheme authScheme,
            ExternalSystemStatus status,
            String baseUrl,
            String credentialHandle,
            Set<String> capabilityCodes) {

        final var system = new ExternalSystem(id);
        system.participantId = participantId;
        system.name = name;
        system.type = type;
        system.direction = direction;
        system.authScheme = authScheme;
        system.status = status;
        system.baseUrl = baseUrl;
        system.credentialHandle = credentialHandle;
        if (capabilityCodes != null) {
            system.capabilityCodes.addAll(capabilityCodes);
        }
        return system;
    }

    /** Publishes the integration so events can flow in its allowed direction. */
    public void activate() {
        if (status == ExternalSystemStatus.RETIRED) {
            throw new InvalidStateException("A retired external system cannot be activated");
        }
        this.status = ExternalSystemStatus.ACTIVE;
        touch();
    }

    /** Temporarily stops exchanging data, keeping the registration. */
    public void suspend() {
        if (status != ExternalSystemStatus.ACTIVE) {
            throw new InvalidStateException(
                    "Only an active external system can be suspended, was " + status);
        }
        this.status = ExternalSystemStatus.SUSPENDED;
        touch();
    }

    /** Decommissions the integration; history is retained for audit. */
    public void retire() {
        if (status == ExternalSystemStatus.RETIRED) {
            throw new InvalidStateException("External system is already retired");
        }
        this.status = ExternalSystemStatus.RETIRED;
        touch();
    }

    /** Declares a capability code this system exposes or consumes. */
    public void exposeCapability(String capabilityCode) {
        if (capabilityCode == null || capabilityCode.isBlank()) {
            throw new BusinessRuleViolation("Capability code is required");
        }
        if (capabilityCodes.add(capabilityCode.trim())) {
            touch();
        }
    }

    public boolean isInbound() {
        return direction == IntegrationDirection.INBOUND
                || direction == IntegrationDirection.BIDIRECTIONAL;
    }

    public boolean isOutbound() {
        return direction == IntegrationDirection.OUTBOUND
                || direction == IntegrationDirection.BIDIRECTIONAL;
    }

    public boolean isUsable() {
        return status == ExternalSystemStatus.ACTIVE;
    }

    public UUID getParticipantId() {
        return participantId;
    }

    public String getName() {
        return name;
    }

    public ExternalSystemType getType() {
        return type;
    }

    public IntegrationDirection getDirection() {
        return direction;
    }

    public AuthScheme getAuthScheme() {
        return authScheme;
    }

    public ExternalSystemStatus getStatus() {
        return status;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getCredentialHandle() {
        return credentialHandle;
    }

    public Set<String> getCapabilityCodes() {
        return Collections.unmodifiableSet(capabilityCodes);
    }

    private void touch() {
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    private static boolean isSandbox(String baseUrl) {
        if (baseUrl == null) {
            return false;
        }
        final var lower = baseUrl.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("sandbox") || lower.contains("localhost") || lower.contains("127.0.0.1");
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleViolation(label + " is required");
        }
        return value.trim();
    }

    @Override
    public String toString() {
        return "ExternalSystem{id=" + getId()
                + ", name='" + name + '\''
                + ", type=" + type
                + ", direction=" + direction
                + ", status=" + status
                + '}';
    }
}

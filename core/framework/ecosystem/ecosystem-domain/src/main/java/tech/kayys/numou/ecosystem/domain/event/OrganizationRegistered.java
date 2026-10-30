package tech.kayys.numou.ecosystem.domain.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.ecosystem.domain.organization.OrganizationType;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Raised when an organization profile is registered against an existing Party identity.
 */
public record OrganizationRegistered(
        UUID eventId,
        Instant occurredAt,
        UUID partyId,
        String legalName,
        String displayName,
        String organizationTypeScheme,
        String organizationTypeCode,
        UUID tenantId
) implements DomainEvent {

    public OrganizationRegistered {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(partyId, "partyId cannot be null");
        Objects.requireNonNull(legalName, "legalName cannot be null");
        Objects.requireNonNull(displayName, "displayName cannot be null");
        Objects.requireNonNull(organizationTypeScheme, "organizationTypeScheme cannot be null");
        Objects.requireNonNull(organizationTypeCode, "organizationTypeCode cannot be null");
    }

    public static OrganizationRegistered of(
            UUID partyId,
            String legalName,
            String displayName,
            OrganizationType organizationType,
            UUID tenantId) {
        Objects.requireNonNull(organizationType, "organizationType cannot be null");
        return new OrganizationRegistered(
                UUID.randomUUID(),
                Instant.now(),
                partyId,
                legalName,
                displayName,
                organizationType.scheme(),
                organizationType.code(),
                tenantId
        );
    }

    @Override
    public String eventType() {
        return "ecosystem.organization.registered";
    }
}

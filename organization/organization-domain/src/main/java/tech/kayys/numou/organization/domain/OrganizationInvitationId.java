package tech.kayys.numou.organization.domain;

import tech.kayys.numou.foundation.domain.identifier.DomainId;
import java.util.Objects;
import java.util.UUID;

public record OrganizationInvitationId(UUID value) implements DomainId<UUID> {
    public OrganizationInvitationId {
        Objects.requireNonNull(value, "value cannot be null");
    }
    public static OrganizationInvitationId generate() {
        return new OrganizationInvitationId(UUID.randomUUID());
    }
    public static OrganizationInvitationId of(UUID value) {
        return new OrganizationInvitationId(value);
    }
}
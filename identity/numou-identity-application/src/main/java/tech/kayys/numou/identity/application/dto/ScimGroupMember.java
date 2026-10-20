package tech.kayys.numou.identity.application.dto;

import java.util.Objects;
import java.util.UUID;

/** One SCIM Group member - always a User in this service. */
public record ScimGroupMember(
        UUID value,
        String display
) {

    public ScimGroupMember {
        Objects.requireNonNull(value, "value cannot be null");
    }
}

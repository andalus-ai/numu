package tech.kayys.syirkah.identity.application.security;

import tech.kayys.syirkah.identity.domain.user.UserId;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record Principal(
        UserId userId,
        String subject,
        String username,
        String displayName,
        Set<String> globalRoles,
        Map<String, Object> attributes,
        PrincipalType type
) {
    public Principal {
        Objects.requireNonNull(subject, "subject cannot be null");
        globalRoles = globalRoles == null ? Set.of() : Set.copyOf(globalRoles);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        Objects.requireNonNull(type, "type cannot be null");
        if (type == PrincipalType.USER && userId == null) {
            throw new IllegalArgumentException("USER principal must have a userId");
        }
    }

    public Principal(
            UserId userId,
            String subject,
            String username,
            String displayName,
            Set<String> globalRoles,
            Map<String, Object> attributes
    ) {
        this(userId, subject, username, displayName, globalRoles, attributes,
                userId == null ? PrincipalType.SERVICE : PrincipalType.USER);
    }

    public boolean authenticated() {
        return !subject.isBlank() && (type == PrincipalType.SERVICE || userId != null);
    }
}

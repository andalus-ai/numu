package tech.kayys.syirkah.identity.adapter.outbound.security;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;
import io.quarkus.security.identity.SecurityIdentity;
import tech.kayys.syirkah.identity.application.port.AuthorizationException;
import tech.kayys.syirkah.identity.application.port.CurrentPrincipalPort;
import tech.kayys.syirkah.identity.application.security.Principal;
import tech.kayys.syirkah.identity.application.security.PrincipalType;
import tech.kayys.syirkah.identity.domain.user.UserId;

import java.util.Objects;
import java.util.UUID;

@ApplicationScoped
public class OidcCurrentPrincipalAdapter implements CurrentPrincipalPort {
    private final SecurityIdentity identity;
    private final JsonWebToken token;

    @Inject
    public OidcCurrentPrincipalAdapter(SecurityIdentity identity, JsonWebToken token) {
        this.identity = identity;
        this.token = token;
    }

    @Override
    public Uni<Principal> current() {
        return Uni.createFrom().deferred(() -> {
            if (identity.isAnonymous()) {
                return Uni.createFrom().failure(new AuthorizationException("authentication.required"));
            }
            var subject = token.getSubject();
            if (subject == null || subject.isBlank()
                    || !Objects.equals(subject, identity.getPrincipal().getName())) {
                return Uni.createFrom().failure(new AuthorizationException("authentication.invalid"));
            }
            var typeClaim = token.getClaim("syirkah_principal_type");
            var type = typeClaim == null ? PrincipalType.USER : principalType(typeClaim.toString());
            var userId = type == PrincipalType.USER
                    ? UserId.of(parseUuid(token.getClaim("syirkah_user_id"), "syirkah_user_id"))
                    : null;
            if (type == PrincipalType.SERVICE) {
                parseUuid(subject, "service subject");
            }
            return Uni.createFrom().item(new Principal(
                    userId,
                    subject,
                    stringClaim("preferred_username"),
                    stringClaim("name"),
                    token.getGroups(),
                    java.util.Map.of(),
                    type
            ));
        });
    }

    private String stringClaim(String name) {
        var value = token.getClaim(name);
        return value == null ? null : value.toString();
    }

    private static UUID parseUuid(Object value, String claim) {
        if (value == null) {
            throw new AuthorizationException("authentication.missing-identity-claim");
        }
        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException invalid) {
            throw new AuthorizationException("authentication.invalid-" + claim);
        }
    }

    private static PrincipalType principalType(String value) {
        try {
            return PrincipalType.valueOf(value.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            throw new AuthorizationException("authentication.invalid-principal-type");
        }
    }
}

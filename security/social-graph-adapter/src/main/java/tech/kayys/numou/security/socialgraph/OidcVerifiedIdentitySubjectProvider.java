package tech.kayys.numou.security.socialgraph;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;
import tech.kayys.numou.ecosystem.domain.party.IdentitySubjectRef;

import java.util.Optional;

/** Reads issuer and subject only from the validated OIDC JWT principal. */
@ApplicationScoped
public class OidcVerifiedIdentitySubjectProvider implements VerifiedIdentitySubjectProvider {
    private final JsonWebToken token;

    @Inject
    public OidcVerifiedIdentitySubjectProvider(JsonWebToken token) {
        this.token = token;
    }

    @Override
    public Optional<IdentitySubjectRef> current() {
        if (token == null || token.getIssuer() == null || token.getIssuer().isBlank()
                || token.getSubject() == null || token.getSubject().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new IdentitySubjectRef(token.getIssuer(), token.getSubject()));
    }
}

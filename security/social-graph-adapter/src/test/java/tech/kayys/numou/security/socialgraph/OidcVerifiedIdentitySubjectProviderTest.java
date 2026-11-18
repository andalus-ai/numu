package tech.kayys.numou.security.socialgraph;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.ecosystem.domain.party.IdentitySubjectRef;

import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;

class OidcVerifiedIdentitySubjectProviderTest {
    @Test
    void returnsIssuerAndSubjectFromValidatedJwt() {
        JsonWebToken token = token("https://issuer.example", "subject-42");
        var provider = new OidcVerifiedIdentitySubjectProvider(token);
        assertThat(provider.current()).contains(new IdentitySubjectRef("https://issuer.example", "subject-42"));
    }

    @Test
    void rejectsMissingIssuerOrSubject() {
        assertThat(new OidcVerifiedIdentitySubjectProvider(token(null, "subject")).current()).isEmpty();
        assertThat(new OidcVerifiedIdentitySubjectProvider(token("https://issuer.example", null)).current()).isEmpty();
    }

    private static JsonWebToken token(String issuer, String subject) {
        return (JsonWebToken) Proxy.newProxyInstance(JsonWebToken.class.getClassLoader(),
                new Class<?>[]{JsonWebToken.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getIssuer" -> issuer;
                    case "getSubject", "getName" -> subject;
                    case "toString" -> "verified-jwt-test";
                    default -> null;
                });
    }
}

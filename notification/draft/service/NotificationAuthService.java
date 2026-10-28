package id.mindo.notification.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.security.KeyFactory;
import java.util.Collection;
import java.util.Base64;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@ApplicationScoped
public class NotificationAuthService {

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "mindo.notification.require-device-auth", defaultValue = "true")
    boolean requireDeviceAuth;

    @ConfigProperty(name = "mindo.notification.internal-api-key")
    Optional<String> internalApiKey;

    @ConfigProperty(name = "mindo.notification.jwt.public-key-location", defaultValue = "classpath:keys/mindo-identity-public.pem")
    String jwtPublicKeyLocation;

    @ConfigProperty(name = "mindo.notification.jwt.issuer", defaultValue = "mindo-identity")
    String jwtIssuer;

    @ConfigProperty(name = "mindo.notification.jwt.audience")
    Optional<String> jwtAudience;

    private volatile PublicKey jwtPublicKey;

    public boolean requiresDeviceAuth() {
        return requireDeviceAuth;
    }

    public boolean isInternalSendAllowed(String presentedKey) {
        final String expected = normalizeOptional(internalApiKey);
        if (expected.isEmpty()) {
            return true;
        }
        return expected.equals(normalize(presentedKey));
    }

    public Optional<AuthPrincipal> principalFromAuthorization(String authorization) {
        final String token = extractBearerToken(authorization);
        if (token.isEmpty()) {
            return Optional.empty();
        }
        try {
            final String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return Optional.empty();
            }
            final Map<String, Object> header = objectMapper.readValue(
                    decodeBase64Url(parts[0]),
                    new TypeReference<Map<String, Object>>() {
                    });
            if (!verifySignature(parts, normalize(String.valueOf(header.get("alg"))))) {
                return Optional.empty();
            }
            final String payload = new String(
                    decodeBase64Url(parts[1]),
                    StandardCharsets.UTF_8);
            final Map<String, Object> claims = objectMapper.readValue(
                    payload,
                    new TypeReference<Map<String, Object>>() {
                    });
            if (!hasExpectedIssuer(claims) || !hasExpectedAudience(claims) || isOutsideValidTimeWindow(claims)) {
                return Optional.empty();
            }
            return Optional.of(new AuthPrincipal(claims));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public boolean principalCanAccessClient(AuthPrincipal principal, String clientId) {
        if (principal == null) {
            return false;
        }
        final String normalizedClientId = normalizeForCompare(clientId);
        return !normalizedClientId.isEmpty() && principal.identifiers().contains(normalizedClientId);
    }

    private String extractBearerToken(String authorization) {
        final String value = normalize(authorization);
        if (value.length() <= 7 || !value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return "";
        }
        return value.substring(7).trim();
    }

    private boolean verifySignature(String[] parts, String algorithm) throws Exception {
        final String javaAlgorithm = switch (algorithm) {
            case "RS256" -> "SHA256withRSA";
            case "RS384" -> "SHA384withRSA";
            case "RS512" -> "SHA512withRSA";
            default -> "";
        };
        if (javaAlgorithm.isEmpty()) {
            return false;
        }
        final Signature verifier = Signature.getInstance(javaAlgorithm);
        verifier.initVerify(loadJwtPublicKey());
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        return verifier.verify(decodeBase64Url(parts[2]));
    }

    private PublicKey loadJwtPublicKey() throws Exception {
        final PublicKey cached = jwtPublicKey;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (jwtPublicKey != null) {
                return jwtPublicKey;
            }
            final byte[] pemBytes = readPublicKeyBytes();
            final String pem = new String(pemBytes, StandardCharsets.UTF_8)
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s+", "");
            final byte[] der = Base64.getDecoder().decode(pem);
            final X509EncodedKeySpec keySpec = new X509EncodedKeySpec(der);
            jwtPublicKey = KeyFactory.getInstance("RSA").generatePublic(keySpec);
            return jwtPublicKey;
        }
    }

    private byte[] readPublicKeyBytes() throws Exception {
        final String location = normalize(jwtPublicKeyLocation);
        if (location.startsWith("classpath:")) {
            final String resourcePath = location.substring("classpath:".length()).replaceFirst("^/+", "");
            try (InputStream stream = Thread.currentThread()
                    .getContextClassLoader()
                    .getResourceAsStream(resourcePath)) {
                if (stream == null) {
                    throw new IllegalStateException("JWT public key resource not found: " + resourcePath);
                }
                return stream.readAllBytes();
            }
        }
        if (location.isEmpty()) {
            throw new IllegalStateException("JWT public key location is required");
        }
        return Files.readAllBytes(Path.of(location));
    }

    private byte[] decodeBase64Url(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    private boolean hasExpectedIssuer(Map<String, Object> claims) {
        final String expected = normalize(jwtIssuer);
        if (expected.isEmpty()) {
            return true;
        }
        return expected.equals(normalize(String.valueOf(claims.get("iss"))));
    }

    private boolean hasExpectedAudience(Map<String, Object> claims) {
        final String expected = normalizeOptional(jwtAudience);
        if (expected.isEmpty()) {
            return true;
        }
        final Object audience = claims.get("aud");
        if (audience instanceof String value) {
            return expected.equals(normalize(value));
        }
        if (audience instanceof Collection<?> values) {
            for (Object value : values) {
                if (expected.equals(normalize(String.valueOf(value)))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isOutsideValidTimeWindow(Map<String, Object> claims) {
        final long nowSeconds = System.currentTimeMillis() / 1000L;
        final Long exp = claimAsLong(claims.get("exp"));
        if (exp == null || exp < nowSeconds - 30L) {
            return true;
        }
        final Long nbf = claimAsLong(claims.get("nbf"));
        if (nbf != null && nbf > nowSeconds + 30L) {
            return true;
        }
        return false;
    }

    private Long claimAsLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            try {
                return Long.parseLong(stringValue.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeOptional(Optional<String> value) {
        return value == null ? "" : normalize(value.orElse(""));
    }

    private static String normalizeForCompare(String value) {
        return normalize(value).toLowerCase();
    }

    public static final class AuthPrincipal {
        private final Set<String> identifiers;

        AuthPrincipal(Map<String, Object> claims) {
            identifiers = new HashSet<>();
            addIdentifier(claims.get("sub"));
            addIdentifier(claims.get("preferred_username"));
            addIdentifier(claims.get("username"));
            addIdentifier(claims.get("upn"));
            addIdentifier(claims.get("email"));
        }

        public Set<String> identifiers() {
            return Set.copyOf(identifiers);
        }

        private void addIdentifier(Object value) {
            if (value == null) {
                return;
            }
            final String normalized = normalizeForCompare(String.valueOf(value));
            if (!normalized.isEmpty()) {
                identifiers.add(normalized);
            }
        }
    }
}

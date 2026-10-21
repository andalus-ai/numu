package io.surveyhub.security;

import io.surveyhub.application.Role;
import io.surveyhub.application.TenantContext;
import io.surveyhub.domain.id.TenantId;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Verifies HS256 bearer tokens and maps them to a {@link TenantContext}.
 *
 * <p>Required claims: {@code sub}, {@code exp}, {@code tenant} (string) and {@code roles} (array of {@link Role} names,
 * unknown names are ignored but at least one must be known). Optional: {@code nbf}, and issuer / audience checks when
 * configured. Only {@code alg=HS256} is accepted (never "none", never an algorithm taken from the token to pick a key
 * type), the signature is compared in constant time, and the secret must be at least 32 bytes.
 */
public final class JwtVerifier {
    private static final int MAX_TOKEN_CHARS = 8 * 1024;

    private final byte[] secret;
    private final String issuer;
    private final String audience;
    private final Clock clock;
    private final Duration leeway;

    /** @param issuer   null/blank = do not check
     *  @param audience null/blank = do not check */
    public JwtVerifier(byte[] secret, String issuer, String audience, Clock clock, Duration leeway) {
        if (secret == null || secret.length < 32) throw new IllegalArgumentException("JWT secret must be at least 32 bytes");
        this.secret = secret.clone();
        this.issuer = issuer == null || issuer.isBlank() ? null : issuer;
        this.audience = audience == null || audience.isBlank() ? null : audience;
        this.clock = clock;
        this.leeway = leeway == null ? Duration.ofSeconds(30) : leeway;
    }

    public TenantContext verify(String token) {
        if (token == null || token.isBlank()) throw new AuthenticationException("Missing token");
        if (token.length() > MAX_TOKEN_CHARS) throw new AuthenticationException("Token too large");
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) throw new AuthenticationException("Not a JWT");

        Map<String, Object> header = object(decode(parts[0]));
        if (!"HS256".equals(header.get("alg"))) throw new AuthenticationException("Unsupported algorithm");

        byte[] expected = hmac(parts[0] + "." + parts[1]);
        byte[] given;
        try { given = Base64.getUrlDecoder().decode(parts[2]); } catch (IllegalArgumentException e) { throw new AuthenticationException("Bad signature encoding"); }
        if (!MessageDigest.isEqual(expected, given)) throw new AuthenticationException("Bad signature");

        Map<String, Object> claims = object(decode(parts[1]));
        long now = clock.instant().getEpochSecond();
        Double exp = number(claims.get("exp"));
        if (exp == null) throw new AuthenticationException("Token has no expiry");
        if (now > exp + leeway.getSeconds()) throw new AuthenticationException("Token expired");
        Double nbf = number(claims.get("nbf"));
        if (nbf != null && now + leeway.getSeconds() < nbf) throw new AuthenticationException("Token not valid yet");
        if (issuer != null && !issuer.equals(claims.get("iss"))) throw new AuthenticationException("Wrong issuer");
        if (audience != null && !hasAudience(claims.get("aud"))) throw new AuthenticationException("Wrong audience");

        String sub = text(claims.get("sub"));
        String tenant = text(claims.get("tenant"));
        if (sub == null || tenant == null) throw new AuthenticationException("Token lacks subject or tenant");

        Set<Role> roles = EnumSet.noneOf(Role.class);
        if (claims.get("roles") instanceof List<?> list) {
            for (Object r : list) {
                if (r instanceof String name) {
                    try { roles.add(Role.valueOf(name.toUpperCase())); } catch (IllegalArgumentException ignored) { /* unknown role */ }
                }
            }
        }
        if (roles.isEmpty()) throw new AuthenticationException("Token carries no known role");
        try {
            return new TenantContext(new TenantId(tenant), sub, roles);
        } catch (RuntimeException e) {
            throw new AuthenticationException("Invalid tenant claim");
        }
    }

    private boolean hasAudience(Object aud) {
        if (aud instanceof String s) return s.equals(audience);
        return aud instanceof List<?> l && l.contains(audience);
    }

    private byte[] hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String decode(String b64) {
        try { return new String(Base64.getUrlDecoder().decode(b64), StandardCharsets.UTF_8); }
        catch (IllegalArgumentException e) { throw new AuthenticationException("Bad base64"); }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(String json) {
        if (MiniJson.parse(json) instanceof Map<?, ?> m) return (Map<String, Object>) m;
        throw new AuthenticationException("Expected a JSON object");
    }

    private static Double number(Object o) { return o instanceof Double d ? d : null; }

    private static String text(Object o) { return o instanceof String s && !s.isBlank() ? s : null; }

    /** Signs a token (HS256). Used by tests and the dev token tool; production tokens come from your identity provider. */
    public static String sign(byte[] secret, Map<String, Object> claims) {
        StringBuilder payload = new StringBuilder("{");
        boolean first = true;
        for (var e : claims.entrySet()) {
            if (!first) payload.append(',');
            first = false;
            payload.append(quote(e.getKey())).append(':').append(json(e.getValue()));
        }
        payload.append('}');
        String head = b64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String body = b64(payload.toString());
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return head + "." + body + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal((head + "." + body).getBytes(StandardCharsets.US_ASCII)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String b64(String s) { return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8)); }

    private static String json(Object v) {
        if (v instanceof Number || v instanceof Boolean) return v.toString();
        if (v instanceof Iterable<?> it) {
            StringBuilder b = new StringBuilder("[");
            boolean first = true;
            for (Object o : it) { if (!first) b.append(','); first = false; b.append(json(o)); }
            return b.append(']').toString();
        }
        return quote(String.valueOf(v));
    }

    private static String quote(String s) { return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
}

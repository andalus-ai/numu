package tech.kayys.numou.mobility.ride.application;

import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Signed, expiring "share my ride" links. A rider can send the link to family; anyone holding it sees a deliberately minimal live view
 * (see {@link PublicTrackingView}) without an account. The token names exactly one ride and one tenant, cannot be extended, and says
 * nothing else; the HMAC prevents forging a link for someone else's ride.
 */
public final class TripShareTokens {

    public record Claims(UUID tenantId, UUID rideId, Instant expiresAt) { }

    private final byte[] secret;

    public TripShareTokens(byte[] secret) {
        if (secret == null || secret.length < 32) throw new IllegalArgumentException("Share secret must be at least 32 bytes");
        this.secret = secret.clone();
    }

    public String issue(Claims c) {
        final String body = b64(("share|" + c.tenantId() + "|" + c.rideId() + "|" + c.expiresAt().getEpochSecond()).getBytes(StandardCharsets.UTF_8));
        return body + "." + b64(mac(body));
    }

    public Claims verify(String token, Instant now) {
        try {
            if (token == null || token.length() > 400) throw invalid();
            final int dot = token.indexOf('.');
            if (dot < 1 || token.indexOf('.', dot + 1) >= 0) throw invalid();
            final String body = token.substring(0, dot);
            if (!MessageDigest.isEqual(Base64.getUrlDecoder().decode(token.substring(dot + 1)), mac(body))) throw invalid();
            final String[] f = new String(Base64.getUrlDecoder().decode(body), StandardCharsets.UTF_8).split("\\|", -1);
            if (f.length != 4 || !"share".equals(f[0])) throw invalid();
            final var claims = new Claims(UUID.fromString(f[1]), UUID.fromString(f[2]), Instant.ofEpochSecond(Long.parseLong(f[3])));
            if (!now.isBefore(claims.expiresAt())) throw invalid();
            return claims;
        } catch (BusinessRuleViolation e) {
            throw e;
        } catch (RuntimeException e) {
            throw invalid();
        }
    }

    private static BusinessRuleViolation invalid() {
        return new BusinessRuleViolation("This link is invalid or has expired");
    }

    private byte[] mac(String body) {
        try {
            final var m = Mac.getInstance("HmacSHA256");
            m.init(new SecretKeySpec(secret, "HmacSHA256"));
            return m.doFinal(("share-link:" + body).getBytes(StandardCharsets.UTF_8));      // domain-separated from quote tokens
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String b64(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}

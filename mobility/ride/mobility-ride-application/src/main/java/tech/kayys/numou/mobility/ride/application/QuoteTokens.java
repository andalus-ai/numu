package tech.kayys.numou.mobility.ride.application;

import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.mobility.kernel.domain.VehicleType;
import tech.kayys.numou.mobility.kernel.geo.LatLng;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Stateless, tamper-proof price quotes.
 *
 * <p>The rider's app gets a token with the route facts the price was computed from (distance, duration, locked surge,
 * promo, the quoted total) and an HMAC over them, bound to the tenant and rider. Booking sends the token back. The server
 * verifies the signature and expiry, recomputes the fare from the signed facts and refuses if it no longer equals the quoted
 * total. A client therefore cannot edit the price, swap the pickup, reuse another rider's token or book after expiry, and no
 * server-side quote storage is needed (any pod can verify).
 */
public final class QuoteTokens {

    public record Claims(UUID tenantId, UUID riderId, VehicleType vehicle, LatLng pickup, LatLng dropoff, double distanceMeters,
                         long durationSeconds, BigDecimal surge, String promoCode, BigDecimal quotedTotal, Instant expiresAt,
                         String pickupLabel, String dropoffLabel) { }

    static final Pattern PROMO = Pattern.compile("^[A-Z0-9_-]{1,32}$");

    private final byte[] secret;

    public QuoteTokens(byte[] secret) {
        if (secret == null || secret.length < 32) {
            throw new IllegalArgumentException("Quote signing secret must be at least 32 bytes");
        }
        this.secret = secret.clone();
    }

    public String issue(Claims c) {
        if (c.promoCode() != null && !PROMO.matcher(c.promoCode()).matches()) {
            throw new IllegalArgumentException("Invalid promo code format");
        }
        final String payload = String.join("|", c.tenantId().toString(), c.riderId().toString(), c.vehicle().name(),
                Double.toString(c.pickup().lat()), Double.toString(c.pickup().lng()), Double.toString(c.dropoff().lat()), Double.toString(c.dropoff().lng()),
                Double.toString(c.distanceMeters()), Long.toString(c.durationSeconds()), c.surge().toPlainString(),
                c.promoCode() == null ? "-" : c.promoCode(), c.quotedTotal().toPlainString(), Long.toString(c.expiresAt().getEpochSecond()),
                b64(clean(c.pickupLabel()).getBytes(StandardCharsets.UTF_8)), b64(clean(c.dropoffLabel()).getBytes(StandardCharsets.UTF_8)));
        final String body = b64(payload.getBytes(StandardCharsets.UTF_8));
        return body + "." + b64(mac(body));
    }

    /** @throws BusinessRuleViolation (generic message) for any tampering, wrong owner, or expiry */
    public Claims verify(String token, UUID tenantId, UUID riderId, Instant now) {
        try {
            if (token == null || token.length() > 1_000) throw new BusinessRuleViolation("Invalid quote");
            final int dot = token.indexOf('.');
            if (dot < 1 || token.indexOf('.', dot + 1) >= 0) throw new BusinessRuleViolation("Invalid quote");
            final String body = token.substring(0, dot);
            final byte[] given = Base64.getUrlDecoder().decode(token.substring(dot + 1));
            if (!MessageDigest.isEqual(given, mac(body))) throw new BusinessRuleViolation("Invalid quote");
            final String[] f = new String(Base64.getUrlDecoder().decode(body), StandardCharsets.UTF_8).split("\\|", -1);
            if (f.length != 15) throw new BusinessRuleViolation("Invalid quote");
            final var claims = new Claims(UUID.fromString(f[0]), UUID.fromString(f[1]), VehicleType.valueOf(f[2]),
                    new LatLng(Double.parseDouble(f[3]), Double.parseDouble(f[4])), new LatLng(Double.parseDouble(f[5]), Double.parseDouble(f[6])),
                    Double.parseDouble(f[7]), Long.parseLong(f[8]), new BigDecimal(f[9]), "-".equals(f[10]) ? null : f[10], new BigDecimal(f[11]),
                    Instant.ofEpochSecond(Long.parseLong(f[12])),
                    new String(Base64.getUrlDecoder().decode(f[13]), StandardCharsets.UTF_8), new String(Base64.getUrlDecoder().decode(f[14]), StandardCharsets.UTF_8));
            if (!claims.tenantId().equals(tenantId) || !claims.riderId().equals(riderId)) throw new BusinessRuleViolation("Invalid quote");
            if (!now.isBefore(claims.expiresAt())) throw new BusinessRuleViolation("The price has expired; please request a new quote");
            return claims;
        } catch (BusinessRuleViolation e) {
            throw e;
        } catch (RuntimeException e) {
            throw new BusinessRuleViolation("Invalid quote"); // malformed anything: never reveal how
        }
    }

    /** Address labels are free text typed by a user: bounded, control characters removed. They are base64-encoded in the token, so no delimiter can be smuggled in. */
    static String clean(String label) {
        if (label == null) return "";
        final String t = label.replaceAll("[\\p{Cntrl}]", " ").trim();
        return t.length() > 120 ? t.substring(0, 120) : t;
    }

    private byte[] mac(String body) {
        try {
            final var m = Mac.getInstance("HmacSHA256");
            m.init(new SecretKeySpec(secret, "HmacSHA256"));
            return m.doFinal(body.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String b64(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}

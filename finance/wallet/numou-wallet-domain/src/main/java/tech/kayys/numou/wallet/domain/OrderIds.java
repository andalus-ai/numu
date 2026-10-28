package tech.kayys.numou.wallet.domain;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Provider-facing references. A payment gateway only echoes back the order id we gave it, so the tenant and the intent must be recoverable
 * from it alone: two UUIDs packed as 2 x 22 URL-safe characters plus a 2-letter prefix = 46 characters (gateways cap order ids around 50).
 * {@code TU} = top-up, {@code PO} = payout.
 */
public final class OrderIds {

    public record Ids(UUID tenantId, UUID entityId) { }

    private OrderIds() { }

    public static String topUp(UUID tenantId, UUID intentId) {
        return encode("TU", tenantId, intentId);
    }

    public static String payout(UUID tenantId, UUID payoutId) {
        return encode("PO", tenantId, payoutId);
    }

    public static Optional<Ids> decodeTopUp(String orderId) {
        return decode("TU", orderId);
    }

    public static Optional<Ids> decodePayout(String orderId) {
        return decode("PO", orderId);
    }

    private static String encode(String prefix, UUID a, UUID b) {
        return prefix + pack(a) + pack(b);
    }

    private static Optional<Ids> decode(String prefix, String s) {
        if (s == null || s.length() != 46 || !s.startsWith(prefix)) return Optional.empty();
        try {
            return Optional.of(new Ids(unpack(s.substring(2, 24)), unpack(s.substring(24))));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static String pack(UUID id) {
        final var b = ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b.array());
    }

    private static UUID unpack(String s) {
        final var b = ByteBuffer.wrap(Base64.getUrlDecoder().decode(s));
        return new UUID(b.getLong(), b.getLong());
    }
}

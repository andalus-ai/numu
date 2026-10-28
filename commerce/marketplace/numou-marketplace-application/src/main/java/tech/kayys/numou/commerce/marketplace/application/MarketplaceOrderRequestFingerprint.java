package tech.kayys.numou.commerce.marketplace.application;

import tech.kayys.numou.commerce.marketplace.domain.MarketplaceOrderLine;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** Stable SHA-256 fingerprint of the semantic order request; never trust a caller-supplied hash. */
public final class MarketplaceOrderRequestFingerprint {
    private MarketplaceOrderRequestFingerprint() { }

    public static String calculate(String tenantId, String buyerId, String currency,
                                   List<MarketplaceOrderLine> lines) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            field(digest, tenantId);
            field(digest, buyerId);
            field(digest, currency.toUpperCase(java.util.Locale.ROOT));
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(lines.size()).array());
            for (MarketplaceOrderLine line : lines) {
                field(digest, line.lineId());
                field(digest, line.productId());
                field(digest, line.offeringId());
                field(digest, line.sellerId());
                field(digest, line.description());
                field(digest, decimal(line.quantity()));
                field(digest, line.unit());
                field(digest, decimal(line.unitPrice()));
                field(digest, line.currency());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", impossible);
        }
    }

    private static String decimal(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static void field(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }
}

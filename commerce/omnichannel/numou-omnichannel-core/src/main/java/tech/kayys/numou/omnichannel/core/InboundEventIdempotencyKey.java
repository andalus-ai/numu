package tech.kayys.numou.omnichannel.core;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Creates a stable opaque idempotency key for downstream handling of a verified event.
 *
 * <p>The identity is scoped by tenant, provider, provider account, and external event ID.
 * Components are length-prefixed before hashing to avoid delimiter ambiguity. The result
 * contains no raw tenant or provider identifiers and is suitable as a downstream
 * idempotency token, subject to the target use case persisting it atomically with effects.</p>
 */
public final class InboundEventIdempotencyKey {
    private static final String PREFIX = "numou-inbound-v1-";

    private InboundEventIdempotencyKey() { }

    public static String from(InboundEventKey key) {
        Objects.requireNonNull(key, "key must not be null");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, key.tenantId().toString());
            update(digest, key.providerId());
            update(digest, key.providerAccountId());
            update(digest, key.externalEventId());
            return PREFIX + HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }
}

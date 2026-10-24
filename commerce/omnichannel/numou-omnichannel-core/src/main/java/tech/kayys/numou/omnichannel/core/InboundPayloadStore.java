package tech.kayys.numou.omnichannel.core;

import java.time.Instant;
import java.util.Optional;

/**
 * Port for short-lived inbound payload storage. Implementations are responsible
 * for encryption at rest, access control, tenant isolation, retention, and
 * secure deletion. Payload bytes must never be written to ordinary logs.
 */
public interface InboundPayloadStore {

    /**
     * Persists payload bytes under the supplied tenant context and retention deadline.
     * The adapter must verify that SHA-256(payload) equals expectedSha256 before
     * accepting the write.
     */
    InboundPayloadReference store(
            ChannelContext context,
            byte[] payload,
            String expectedSha256,
            Instant retentionUntil);

    /** Reads payload only for an authorized tenant-scoped processing operation. */
    Optional<byte[]> load(ChannelContext context, InboundPayloadReference reference);

    /** Deletes payload bytes when retention expires or an authorized purge is requested. */
    boolean delete(ChannelContext context, InboundPayloadReference reference);
}

package tech.kayys.numou.queuemanagement.spi;

/**
 * Explicit codec for a command result stored in the idempotency table.
 *
 * Implementations must use a stable, versioned wire representation and reject payloads
 * that do not match their declared result type. The transaction adapter must persist
 * {@link #resultType()} alongside the encoded payload and select a codec by that exact
 * stable identifier when replaying a completed command. Java class names are not stable
 * identifiers across refactors and must not be used as resultType values.
 */
public interface QueueIdempotencyResultCodec<T> {
    /** Stable schema identifier, for example "queue-entry.v1". */
    String resultType();

    /** Exact Java result class handled by this codec. */
    Class<T> javaType();

    /** Encode a completed result into JSON suitable for queue_idempotency.result_payload. */
    String encode(T result);

    /** Decode a previously encoded result payload. Must validate required fields and schema. */
    T decode(String payload);
}

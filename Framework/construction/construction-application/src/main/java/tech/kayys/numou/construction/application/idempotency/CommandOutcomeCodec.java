package tech.kayys.numou.construction.application.idempotency;

/** JSON codec abstraction; bind to the application's configured Jackson/JSON-B mapper. */
public interface CommandOutcomeCodec {
    String encode(Object value);
    <T> T decode(String json, Class<T> resultType);
}

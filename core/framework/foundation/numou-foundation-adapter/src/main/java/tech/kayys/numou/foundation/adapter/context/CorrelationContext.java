package tech.kayys.numou.foundation.adapter.context;

import java.util.UUID;

/**
 * Correlation id of the current request. Same dual strategy as {@link TenantContext}. Unlike the previous version,
 * reading it never stores a generated id on the thread (that id then leaked into unrelated later requests).
 */
public final class CorrelationContext {

    public static final String HEADER_CORRELATION_ID = "X-Correlation-ID";

    private static final ThreadLocal<String> FALLBACK = new ThreadLocal<>();

    private CorrelationContext() { }

    public static String getCorrelationId() {
        final var holder = TenantContext.holder();
        final String id = holder != null ? holder.correlationId() : FALLBACK.get();
        return id != null ? id : UUID.randomUUID().toString();
    }

    public static void setCorrelationId(String correlationId) {
        final var holder = TenantContext.holder();
        if (holder != null) {
            holder.correlationId(correlationId);
        } else {
            FALLBACK.set(correlationId);
        }
    }

    public static void clear() {
        FALLBACK.remove();
    }
}

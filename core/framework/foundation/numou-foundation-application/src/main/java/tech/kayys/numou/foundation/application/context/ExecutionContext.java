package tech.kayys.numou.foundation.application.context;

import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.time.ZoneId;
import java.util.*;

/**
 * Canonical application runtime context (Docs/Enhancement/enhance02.md §P2.5).
 *
 * <p>Represents the business execution metadata of an incoming operation across the platform.
 * Pure Java, framework-neutral.
 */
public record ExecutionContext(
        TenantId tenantId,
        CorrelationId correlationId,
        CausationId causationId,
        ActorId actorId,
        String traceId,
        String spanId,
        Instant now,
        Locale locale,
        ZoneId timeZone,
        Map<String, Object> attributes
) {

    public ExecutionContext {
        correlationId = correlationId != null ? correlationId : CorrelationId.generate();
        causationId = causationId != null ? causationId : CausationId.of(correlationId.value());
        actorId = actorId != null ? actorId : ActorId.anonymous();
        traceId = traceId != null ? traceId : correlationId.value();
        spanId = spanId != null ? spanId : "";
        now = now != null ? now : Instant.now();
        locale = locale != null ? locale : Locale.getDefault();
        timeZone = timeZone != null ? timeZone : ZoneId.systemDefault();
        attributes = attributes != null ? Collections.unmodifiableMap(new LinkedHashMap<>(attributes)) : Map.of();
    }

    public static ExecutionContext empty() {
        return new ExecutionContext(
                null,
                CorrelationId.generate(),
                null,
                ActorId.anonymous(),
                null,
                null,
                Instant.now(),
                Locale.getDefault(),
                ZoneId.systemDefault(),
                Map.of()
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public TenantId requireTenant() {
        if (tenantId == null) {
            throw new IllegalStateException("Tenant context is required for tenant-scoped operation");
        }
        return tenantId;
    }

    public ExecutionContext withTenantId(TenantId tenantId) {
        return new ExecutionContext(tenantId, correlationId, causationId, actorId, traceId, spanId, now, locale, timeZone, attributes);
    }

    public ExecutionContext withActorId(ActorId actorId) {
        return new ExecutionContext(tenantId, correlationId, causationId, actorId, traceId, spanId, now, locale, timeZone, attributes);
    }

    public ExecutionContext withCorrelationId(CorrelationId correlationId) {
        return new ExecutionContext(tenantId, correlationId, causationId, actorId, traceId, spanId, now, locale, timeZone, attributes);
    }

    public ExecutionContext withCausationId(CausationId causationId) {
        return new ExecutionContext(tenantId, correlationId, causationId, actorId, traceId, spanId, now, locale, timeZone, attributes);
    }

    public ExecutionContext withTrace(String traceId, String spanId) {
        return new ExecutionContext(tenantId, correlationId, causationId, actorId, traceId, spanId, now, locale, timeZone, attributes);
    }

    public ExecutionContext withAttribute(String key, Object value) {
        var map = new LinkedHashMap<>(this.attributes);
        map.put(key, value);
        return new ExecutionContext(tenantId, correlationId, causationId, actorId, traceId, spanId, now, locale, timeZone, map);
    }

    public static final class Builder {
        private TenantId tenantId;
        private CorrelationId correlationId;
        private CausationId causationId;
        private ActorId actorId;
        private String traceId;
        private String spanId;
        private Instant now;
        private Locale locale;
        private ZoneId timeZone;
        private final Map<String, Object> attributes = new LinkedHashMap<>();

        public Builder tenantId(TenantId tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder correlationId(CorrelationId correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder causationId(CausationId causationId) {
            this.causationId = causationId;
            return this;
        }

        public Builder actorId(ActorId actorId) {
            this.actorId = actorId;
            return this;
        }

        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public Builder spanId(String spanId) {
            this.spanId = spanId;
            return this;
        }

        public Builder now(Instant now) {
            this.now = now;
            return this;
        }

        public Builder locale(Locale locale) {
            this.locale = locale;
            return this;
        }

        public Builder timeZone(ZoneId timeZone) {
            this.timeZone = timeZone;
            return this;
        }

        public Builder attribute(String key, Object value) {
            this.attributes.put(key, value);
            return this;
        }

        public ExecutionContext build() {
            return new ExecutionContext(
                    tenantId,
                    correlationId,
                    causationId,
                    actorId,
                    traceId,
                    spanId,
                    now,
                    locale,
                    timeZone,
                    attributes
            );
        }
    }
}

package tech.kayys.syirkah.identity.application.security.abac;

import java.util.Map;
import java.util.Objects;
import java.util.Collection;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AuthorizationContext(
        String tenantId,
        String permission,
        Map<String, Object> subject,
        Map<String, Object> resource,
        Map<String, Object> environment
) {
    public AuthorizationContext {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(permission, "permission cannot be null");
        subject = freezeMap(subject);
        resource = freezeMap(resource);
        environment = freezeMap(environment);
    }

    Object value(AttributeReference reference) {
        var namespace = switch (reference.namespace()) {
            case SUBJECT -> subject;
            case RESOURCE -> resource;
            case ENVIRONMENT -> environment;
        };
        var current = (Object) namespace;
        for (var segment : reference.name().split("\\.")) {
            if (!(current instanceof Map<?, ?> map) || !map.containsKey(segment)) {
                return null;
            }
            current = map.get(segment);
        }
        return current;
    }

    private static Map<String, Object> freezeMap(Map<String, Object> values) {
        Objects.requireNonNull(values, "attribute maps cannot be null");
        var frozen = new LinkedHashMap<String, Object>();
        values.forEach((key, value) -> {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("Attribute keys cannot be blank");
            }
            frozen.put(key, freeze(value));
        });
        return Collections.unmodifiableMap(frozen);
    }

    private static Object freeze(Object value) {
        if (value == null || value instanceof String || value instanceof Boolean || value instanceof UUID
                || value instanceof Instant || value instanceof LocalDate || value instanceof BigDecimal
                || value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long
                || value instanceof Float floatValue && Float.isFinite(floatValue)
                || value instanceof Double doubleValue && Double.isFinite(doubleValue)) {
            return value;
        }
        if (value instanceof Float || value instanceof Double) {
            throw new IllegalArgumentException("Authorization numeric attributes must be finite");
        }
        if (value instanceof Map<?, ?> map) {
            var nested = new LinkedHashMap<String, Object>();
            map.forEach((key, item) -> {
                if (!(key instanceof String stringKey) || stringKey.isBlank()) {
                    throw new IllegalArgumentException("Nested attribute keys must be non-blank strings");
                }
                nested.put(stringKey, freeze(item));
            });
            return Collections.unmodifiableMap(nested);
        }
        if (value instanceof Collection<?> collection) {
            return Collections.unmodifiableList(collection.stream().map(AuthorizationContext::freeze).toList());
        }
        throw new IllegalArgumentException("Unsupported authorization attribute type: " + value.getClass().getName());
    }
}

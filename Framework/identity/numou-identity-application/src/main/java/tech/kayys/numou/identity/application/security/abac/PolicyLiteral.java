package tech.kayys.syirkah.identity.application.security.abac;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

final class PolicyLiteral {
    private PolicyLiteral() {}

    static Object requireSupported(Object value) {
        if (value instanceof String || value instanceof Boolean || value instanceof UUID
                || value instanceof Instant || value instanceof LocalDate || value instanceof BigDecimal
                || value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long
                || value instanceof Float floatValue && Float.isFinite(floatValue)
                || value instanceof Double doubleValue && Double.isFinite(doubleValue)) {
            return value;
        }
        throw new IllegalArgumentException("Policy literals must be finite primitive values");
    }
}

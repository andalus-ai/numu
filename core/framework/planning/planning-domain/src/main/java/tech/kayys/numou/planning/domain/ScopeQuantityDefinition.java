package tech.kayys.numou.planning.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable, versioned quantity attached to an opaque scope/work-item reference.
 * Deliberately excludes currency, pricing, tax, and construction-specific semantics.
 */
public record ScopeQuantityDefinition(
        UUID id,
        UUID tenantId,
        String scopeType,
        String scopeId,
        String code,
        String description,
        String unitCode,
        BigDecimal plannedQuantity,
        int revision) {

    public ScopeQuantityDefinition {
        Objects.requireNonNull(id, "Definition id cannot be null");
        Objects.requireNonNull(tenantId, "Tenant id cannot be null");
        scopeType = required(scopeType, "Scope type");
        scopeId = required(scopeId, "Scope id");
        code = required(code, "Quantity code");
        description = required(description, "Description");
        unitCode = required(unitCode, "Unit code").toUpperCase(java.util.Locale.ROOT);
        Objects.requireNonNull(plannedQuantity, "Planned quantity cannot be null");
        if (plannedQuantity.signum() < 0) throw new IllegalArgumentException("Planned quantity cannot be negative");
        if (plannedQuantity.scale() > 6) throw new IllegalArgumentException("Planned quantity supports at most six decimal places");
        if (revision < 1) throw new IllegalArgumentException("Revision must be positive");
        if (scopeType.length() > 100 || scopeId.length() > 240 || code.length() > 120
                || description.length() > 1000 || unitCode.length() > 32) {
            throw new IllegalArgumentException("Quantity definition field exceeds supported length");
        }
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " cannot be null");
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " cannot be blank");
        return normalized;
    }
}

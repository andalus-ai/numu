package tech.kayys.numou.accounting.application.ap;

import java.util.Objects;

/** Tenant and actor identity resolved from a verified host security context. */
public record ApRequestContext(String tenantId, String actorId) {
    public ApRequestContext {
        tenantId = required(tenantId, "tenantId");
        actorId = required(actorId, "actorId");
    }

    private static String required(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}

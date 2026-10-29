package tech.kayys.numou.pos.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Neutral physical or logical POS register identity.
 *
 * <p>Branch/store references are opaque IDs owned by organization/location
 * capabilities. POS does not duplicate their aggregates or validation rules.</p>
 */
public record Register(
        UUID id,
        UUID tenantId,
        UUID branchId,
        UUID storeId,
        String code,
        boolean enabled) {

    public Register {
        Objects.requireNonNull(id, "register id cannot be null");
        Objects.requireNonNull(tenantId, "tenant id cannot be null");
        Objects.requireNonNull(branchId, "branch id cannot be null");
        Objects.requireNonNull(storeId, "store id cannot be null");
        Objects.requireNonNull(code, "register code cannot be null");
        code = code.trim();
        if (code.isEmpty()) {
            throw new IllegalArgumentException("register code cannot be blank");
        }
    }

    public Register disable() {
        return new Register(id, tenantId, branchId, storeId, code, false);
    }

    public Register enable() {
        return new Register(id, tenantId, branchId, storeId, code, true);
    }
}

package tech.kayys.numou.support.application.email;

import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.util.Objects;

public record EmailRoute(TenantId tenantId, String queueCode) {
    public EmailRoute {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        if (queueCode == null || queueCode.isBlank()) {
            throw new IllegalArgumentException("queueCode cannot be blank");
        }
        queueCode = queueCode.trim();
    }
}

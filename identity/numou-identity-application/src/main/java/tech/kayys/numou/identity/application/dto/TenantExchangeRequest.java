package tech.kayys.numou.identity.application.dto;

import java.util.UUID;

/** Tenant selection candidate; active membership is revalidated before token issuance. */
public record TenantExchangeRequest(UUID tenantId) {
}

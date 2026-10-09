package tech.kayys.syirkah.survey.adapter.rest;

import tech.kayys.syirkah.survey.application.TenantContext;
import tech.kayys.syirkah.survey.domain.DomainException;
import tech.kayys.syirkah.survey.domain.ErrorCode;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;
import jakarta.enterprise.context.RequestScoped;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Holds the tenant resolved for the current HTTP request. */
@RequestScoped
public class RequestTenant {
    private TenantContext ctx;

    void set(TenantContext ctx) { this.ctx = ctx; }

    public TenantContext get() {
        if (ctx == null) throw new DomainException(ErrorCode.FORBIDDEN, "No tenant resolved for this request");
        return ctx;
    }

    public static TenantContext of(String tenantId, String actor) {
        return new TenantContext(parseTenantId(tenantId), actor);
    }

    public static TenantId parseTenantId(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "tenant id must not be blank");
        }
        try {
            return TenantId.of(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            return TenantId.of(UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)));
        }
    }
}

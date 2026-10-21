package io.surveyhub.adapter.rest;

import io.surveyhub.application.TenantContext;
import io.surveyhub.domain.DomainException;
import io.surveyhub.domain.ErrorCode;
import jakarta.enterprise.context.RequestScoped;

/** Holds the tenant resolved for the current HTTP request. */
@RequestScoped
public class RequestTenant {
    private TenantContext ctx;

    void set(TenantContext ctx) { this.ctx = ctx; }

    public TenantContext get() {
        if (ctx == null) throw new DomainException(ErrorCode.FORBIDDEN, "No tenant resolved for this request");
        return ctx;
    }

}

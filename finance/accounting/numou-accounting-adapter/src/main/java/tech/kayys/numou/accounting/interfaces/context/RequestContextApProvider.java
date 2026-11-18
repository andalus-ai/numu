package tech.kayys.numou.accounting.interfaces.context;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.accounting.application.ap.ApRequestContext;
import tech.kayys.numou.accounting.application.ap.ApRequestContextProvider;
import tech.kayys.numou.foundation.adapter.context.RequestContextHolder;

/** Bridges AP to the shared security filter's verified request context. */
@ApplicationScoped
public class RequestContextApProvider implements ApRequestContextProvider {
    private final RequestContextHolder requestContext;

    @Inject
    public RequestContextApProvider(RequestContextHolder requestContext) {
        this.requestContext = requestContext;
    }

    @Override
    public ApRequestContext requireContext() {
        if (!requestContext.tenantVerified()) {
            throw new SecurityException("AP requires a verified tenant context");
        }
        String tenantId = requestContext.tenantId();
        String actorId = requestContext.actor();
        if (tenantId == null || tenantId.isBlank() || actorId == null || actorId.isBlank()) {
            throw new SecurityException("AP requires verified tenant and actor identity");
        }
        return new ApRequestContext(tenantId, actorId);
    }
}

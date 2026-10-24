package tech.kayys.numou.courier.application.port;

import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Trusted application authorization boundary for resolving a dispatch-wave discrepancy.
 * Implementations must derive permissions from the authenticated principal and policy
 * engine; callers must not be able to provide an authorization reference themselves.
 */
public interface DispatchWaveDiscrepancyResolutionAuthorizer {
    CompletionStage<Decision> authorize(Request request);

    record Request(TenantId tenantId, String actorReference, UUID waveId, String resolutionCode) { }

    record Decision(boolean allowed, String decisionReference) { }
}
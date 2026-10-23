package tech.kayys.numou.planning.application.port;

import tech.kayys.numou.planning.domain.QuantityCorrectionDecision;
import java.util.List;
import java.util.UUID;

/** Tenant-explicit append-only correction review persistence boundary. */
public interface QuantityCorrectionDecisionRepository {
    /** Returns false for an identical idempotent replay; conflicting key reuse is rejected. */
    boolean appendDecision(QuantityCorrectionDecision decision);
    List<QuantityCorrectionDecision> findDecisions(UUID tenantId, UUID definitionId);
}

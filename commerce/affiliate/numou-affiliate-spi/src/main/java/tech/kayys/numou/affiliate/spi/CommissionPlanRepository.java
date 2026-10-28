package tech.kayys.numou.affiliate.spi;

import tech.kayys.numou.affiliate.domain.CommissionPlan;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Tenant/program-scoped lookup of the immutable commission plan effective at conversion time. */
public interface CommissionPlanRepository {
    CompletionStage<Optional<CommissionPlan>> findEffective(String tenantId, UUID programId, Instant occurredAt);
}

package tech.kayys.numou.affiliate.spi;

import tech.kayys.numou.affiliate.domain.AffiliateTouchpoint;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Tenant-scoped bounded query for touchpoints that can qualify for a conversion. */
public interface AffiliateTouchpointRepository {
    CompletionStage<List<AffiliateTouchpoint>> findEligible(
            String tenantId, UUID programId, String subjectKey, Instant notBefore, Instant notAfter);
}

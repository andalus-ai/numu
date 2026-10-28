package tech.kayys.numou.construction.spi.consultancy;

import tech.kayys.numou.construction.domain.consultancy.ConsultancyEngagement;
import tech.kayys.numou.construction.domain.consultancy.ConsultancyEngagementId;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Persistence boundary for consultant engagements.
 *
 * <p>Every lookup is explicitly tenant-scoped. Implementations must enforce
 * uniqueness of (tenantId, engagementNumber) with a storage-level constraint;
 * a preflight lookup alone is not safe under concurrent requests. Save and
 * any command-idempotency/outbox records must share a transaction when used
 * by an idempotent command handler.</p>
 */
public interface ConsultancyEngagementRepository {
    CompletionStage<Optional<ConsultancyEngagement>> findById(
            String tenantId, ConsultancyEngagementId engagementId);

    CompletionStage<Optional<ConsultancyEngagement>> findByEngagementNumber(
            String tenantId, String engagementNumber);

    CompletionStage<ConsultancyEngagement> save(
            String tenantId, ConsultancyEngagement engagement);
}

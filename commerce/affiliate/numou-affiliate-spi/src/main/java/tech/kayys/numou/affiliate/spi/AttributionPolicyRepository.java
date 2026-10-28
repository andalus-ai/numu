package tech.kayys.numou.affiliate.spi;

import tech.kayys.numou.affiliate.domain.AttributionPolicy;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Tenant-scoped policy lookup; adapters must return the immutable version effective at conversion time. */
public interface AttributionPolicyRepository {
    CompletionStage<Optional<AttributionPolicy>> findEffective(
            String tenantId, UUID programId, Instant conversionAt);
}

package tech.kayys.numou.affiliate.spi;
import tech.kayys.numou.affiliate.domain.AttributionPolicy;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
/** Returns immutable policy version effective at the source conversion timestamp. */
public interface AttributionPolicyRepository {
    CompletionStage<Optional<AttributionPolicy>> findEffective(String tenantId, UUID programId, Instant conversionAt);
}

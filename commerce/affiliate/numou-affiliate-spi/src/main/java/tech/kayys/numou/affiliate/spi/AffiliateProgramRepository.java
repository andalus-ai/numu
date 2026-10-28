package tech.kayys.numou.affiliate.spi;

import tech.kayys.numou.affiliate.domain.AffiliateProgram;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Tenant-scoped persistence contract; implementations must enforce tenant scope in the query, not after loading. */
public interface AffiliateProgramRepository {
    CompletionStage<AffiliateProgram> save(AffiliateProgram program);
    CompletionStage<Optional<AffiliateProgram>> findById(String tenantId, UUID programId);
    CompletionStage<Boolean> existsByName(String tenantId, String normalizedName);
}

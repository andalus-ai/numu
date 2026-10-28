package tech.kayys.numou.affiliate.spi;

import tech.kayys.numou.affiliate.domain.AffiliateReferralLink;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Tenant-scoped link contract. Code uniqueness must be enforced atomically by the backing store. */
public interface AffiliateReferralLinkRepository {
    CompletionStage<AffiliateReferralLink> save(AffiliateReferralLink link);
    CompletionStage<Optional<AffiliateReferralLink>> findById(String tenantId, UUID linkId);
    CompletionStage<Optional<AffiliateReferralLink>> findByCode(String tenantId, String normalizedCode);
}

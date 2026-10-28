package tech.kayys.numou.affiliate.spi;

import tech.kayys.numou.affiliate.domain.AffiliatePartner;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Tenant-scoped partner persistence contract. */
public interface AffiliatePartnerRepository {
    CompletionStage<AffiliatePartner> save(AffiliatePartner partner);
    CompletionStage<Optional<AffiliatePartner>> findById(String tenantId, UUID partnerId);
}

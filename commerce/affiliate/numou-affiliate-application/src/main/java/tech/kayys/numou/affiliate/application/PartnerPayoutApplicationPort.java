package tech.kayys.numou.affiliate.application;

import tech.kayys.numou.affiliate.domain.PartnerPayout;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface PartnerPayoutApplicationPort {
    CompletionStage<PartnerPayout> requestPayout(
            AffiliateCommandContext context, UUID commissionEntryId, String destinationId);

    CompletionStage<PartnerPayout> submitPayout(
            AffiliateCommandContext context, UUID payoutId);

    CompletionStage<PartnerPayout> reconcilePayout(
            AffiliateCommandContext context, UUID payoutId);
}

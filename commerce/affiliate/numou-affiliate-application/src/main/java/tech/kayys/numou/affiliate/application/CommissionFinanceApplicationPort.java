package tech.kayys.numou.affiliate.application;

import tech.kayys.numou.affiliate.domain.CommissionFinancePosting;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface CommissionFinanceApplicationPort {
    CompletionStage<CommissionFinancePosting> requestLiabilityPosting(
            AffiliateCommandContext context, UUID commissionEntryId);

    CompletionStage<Integer> dispatchPending(
            AffiliateCommandContext context, String workerId, int limit);

    CompletionStage<Boolean> reconcile(
            AffiliateCommandContext context, UUID postingId);
}

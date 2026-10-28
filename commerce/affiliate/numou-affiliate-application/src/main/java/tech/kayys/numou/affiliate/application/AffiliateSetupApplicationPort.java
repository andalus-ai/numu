package tech.kayys.numou.affiliate.application;

import tech.kayys.numou.affiliate.domain.AffiliatePartner;
import tech.kayys.numou.affiliate.domain.AffiliateProgram;
import tech.kayys.numou.affiliate.domain.AffiliateReferralLink;
import tech.kayys.numou.affiliate.domain.ProgramMembership;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Consumer-facing application contract for initial affiliate setup.
 * Runtime adapters must authorize the actor and persist idempotency before side effects.
 */
public interface AffiliateSetupApplicationPort {
    CompletionStage<AffiliateProgram> createProgram(
            AffiliateCommandContext context, UUID programId, String name);

    CompletionStage<AffiliatePartner> registerPartner(
            AffiliateCommandContext context, UUID partnerId, String displayName);

    CompletionStage<ProgramMembership> invitePartner(
            AffiliateCommandContext context, UUID membershipId, UUID programId, UUID partnerId);

    CompletionStage<AffiliateReferralLink> issueReferralLink(
            AffiliateCommandContext context, UUID linkId, UUID programId, UUID partnerId, String code);
}

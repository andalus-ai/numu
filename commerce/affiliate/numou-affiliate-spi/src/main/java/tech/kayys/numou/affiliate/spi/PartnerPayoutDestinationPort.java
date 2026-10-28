package tech.kayys.numou.affiliate.spi;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/** Returns metadata only; sensitive bank/wallet destination details remain owned by the destination capability. */
public interface PartnerPayoutDestinationPort {
    CompletionStage<Optional<VerifiedDestination>> findVerified(
            String tenantId, String partnerId, String destinationId, String currency);

    record VerifiedDestination(
            String tenantId,
            String partnerId,
            String destinationId,
            String currency,
            boolean active,
            boolean verified) {
    }
}

package tech.kayys.numou.crm.application.api.command;

import tech.kayys.numou.crm.domain.referral.ReferralId;
import tech.kayys.numou.foundation.application.command.Command;
import java.util.Objects;

/**
 * Command to cancel a referral.
 */
public record CancelReferralCommand(
        ReferralId referralId
) implements Command {

    public CancelReferralCommand {
        Objects.requireNonNull(referralId, "referralId cannot be null");
    }
}
package tech.kayys.numou.crm.application.api.command;

import tech.kayys.numou.crm.domain.referral.ReferralId;
import tech.kayys.numou.foundation.application.command.Command;
import java.util.Objects;

/**
 * Command to reject a referral.
 */
public record RejectReferralCommand(
        ReferralId referralId
) implements Command {

    public RejectReferralCommand {
        Objects.requireNonNull(referralId, "referralId cannot be null");
    }
}
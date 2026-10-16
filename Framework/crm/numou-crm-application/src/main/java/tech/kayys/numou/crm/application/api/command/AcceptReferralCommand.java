package tech.kayys.numou.crm.application.api.command;

import tech.kayys.numou.crm.domain.referral.ReferralId;
import tech.kayys.numou.foundation.application.command.Command;
import java.util.Objects;

/**
 * Command to accept a referral.
 */
public record AcceptReferralCommand(
        ReferralId referralId
) implements Command {

    public AcceptReferralCommand {
        Objects.requireNonNull(referralId, "referralId cannot be null");
    }
}
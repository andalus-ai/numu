package tech.kayys.numou.crm.application.api.command;

import tech.kayys.numou.crm.domain.identifier.AccountId;
import tech.kayys.numou.crm.domain.referral.ReferralId;
import tech.kayys.numou.crm.domain.referral.ReferralTarget;
import tech.kayys.numou.crm.domain.referral.ReferralType;
import tech.kayys.numou.foundation.application.command.Command;
import java.util.Objects;

/**
 * Command to create a new referral.
 */
public record CreateReferralCommand(
        ReferralId referralId,
        AccountId referrerAccountId,
        ReferralTarget target,
        ReferralType type,
        String code,
        String description
) implements Command {

    public CreateReferralCommand {
        Objects.requireNonNull(referralId, "referralId cannot be null");
        Objects.requireNonNull(referrerAccountId, "referrerAccountId cannot be null");
        Objects.requireNonNull(target, "target cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(code, "code cannot be null");
        Objects.requireNonNull(description, "description cannot be null");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ReferralId referralId;
        private AccountId referrerAccountId;
        private ReferralTarget target;
        private ReferralType type;
        private String code;
        private String description;

        public Builder referralId(ReferralId referralId) {
            this.referralId = referralId;
            return this;
        }

        public Builder referrerAccountId(AccountId referrerAccountId) {
            this.referrerAccountId = referrerAccountId;
            return this;
        }

        public Builder target(ReferralTarget target) {
            this.target = target;
            return this;
        }

        public Builder type(ReferralType type) {
            this.type = type;
            return this;
        }

        public Builder code(String code) {
            this.code = code;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public CreateReferralCommand build() {
            if (referralId == null) {
                referralId = ReferralId.random();
            }
            return new CreateReferralCommand(
                    referralId, referrerAccountId, target, type, code, description
            );
        }
    }
}
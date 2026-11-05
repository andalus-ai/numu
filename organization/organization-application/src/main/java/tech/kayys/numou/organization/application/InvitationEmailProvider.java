package tech.kayys.numou.organization.application;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Provider-neutral email boundary. Implementations must use bounded network timeouts and honor idempotencyKey. */
@FunctionalInterface
public interface InvitationEmailProvider {
    CompletionStage<Void> send(Email message);

    record Email(UUID idempotencyKey, String recipient, String invitationUrl) {
        public Email {
            if (idempotencyKey == null || recipient == null || recipient.isBlank()
                    || invitationUrl == null || invitationUrl.isBlank())
                throw new IllegalArgumentException("Idempotency key, recipient and invitation URL are required");
        }
        @Override public String toString() {
            return "Email[idempotencyKey=" + idempotencyKey + ", recipient=" + recipient
                    + ", invitationUrl=[REDACTED]]";
        }
    }
}

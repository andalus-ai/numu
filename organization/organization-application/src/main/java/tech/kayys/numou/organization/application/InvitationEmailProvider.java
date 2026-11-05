package tech.kayys.numou.organization.application;

import java.util.concurrent.CompletionStage;

/** Provider-neutral email boundary. Implementations must use bounded network timeouts. */
@FunctionalInterface
public interface InvitationEmailProvider {
    CompletionStage<Void> send(Email message);

    record Email(String recipient, String invitationUrl) {
        public Email {
            if (recipient == null || recipient.isBlank() || invitationUrl == null || invitationUrl.isBlank())
                throw new IllegalArgumentException("Recipient and invitation URL are required");
        }
        @Override public String toString() {
            return "Email[recipient=" + recipient + ", invitationUrl=[REDACTED]]";
        }
    }
}

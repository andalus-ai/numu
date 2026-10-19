package tech.kayys.numou.crm.application.api.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.crm.domain.identifier.EmailMessageId;

import java.util.List;
import java.util.Map;

/**
 * Command to send an email.
 */
public record SendEmailCommand(
        EmailMessageId messageId,
        String fromEmail,
        String fromName,
        List<String> to,
        List<String> cc,
        List<String> bcc,
        String subject,
        String body,
        String htmlBody,
        String replyTo,
        List<String> attachments,
        Map<String, String> variables,
        boolean trackOpens,
        boolean trackClicks
) implements Command {

    public SendEmailCommand {
        if (to == null || to.isEmpty()) {
            throw new IllegalArgumentException("At least one recipient is required");
        }
        if (subject == null || subject.trim().isEmpty()) {
            throw new IllegalArgumentException("Subject cannot be empty");
        }
        if (body == null || body.trim().isEmpty()) {
            throw new IllegalArgumentException("Body cannot be empty");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private EmailMessageId messageId;
        private String fromEmail;
        private String fromName;
        private List<String> to;
        private List<String> cc;
        private List<String> bcc;
        private String subject;
        private String body;
        private String htmlBody;
        private String replyTo;
        private List<String> attachments;
        private Map<String, String> variables;
        private boolean trackOpens = true;
        private boolean trackClicks = true;

        public Builder messageId(EmailMessageId messageId) {
            this.messageId = messageId;
            return this;
        }

        public Builder fromEmail(String fromEmail) {
            this.fromEmail = fromEmail;
            return this;
        }

        public Builder fromName(String fromName) {
            this.fromName = fromName;
            return this;
        }

        public Builder to(List<String> to) {
            this.to = to;
            return this;
        }

        public Builder cc(List<String> cc) {
            this.cc = cc;
            return this;
        }

        public Builder bcc(List<String> bcc) {
            this.bcc = bcc;
            return this;
        }

        public Builder subject(String subject) {
            this.subject = subject;
            return this;
        }

        public Builder body(String body) {
            this.body = body;
            return this;
        }

        public Builder htmlBody(String htmlBody) {
            this.htmlBody = htmlBody;
            return this;
        }

        public Builder replyTo(String replyTo) {
            this.replyTo = replyTo;
            return this;
        }

        public Builder attachments(List<String> attachments) {
            this.attachments = attachments;
            return this;
        }

        public Builder variables(Map<String, String> variables) {
            this.variables = variables;
            return this;
        }

        public Builder trackOpens(boolean trackOpens) {
            this.trackOpens = trackOpens;
            return this;
        }

        public Builder trackClicks(boolean trackClicks) {
            this.trackClicks = trackClicks;
            return this;
        }

        public SendEmailCommand build() {
            if (messageId == null) {
                messageId = EmailMessageId.generate();
            }
            return new SendEmailCommand(
                messageId, fromEmail, fromName, to, cc, bcc,
                subject, body, htmlBody, replyTo, attachments,
                variables, trackOpens, trackClicks
            );
        }
    }
}
package tech.kayys.numou.crm.application.api.command;

import tech.kayys.numou.foundation.application.command.Command;

public record CreateEmailTemplateCommand(
        String name,
        String subject,
        String body,
        String htmlBody,
        String category,
        String fromEmail,
        String fromName,
        String replyTo
) implements Command {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String name;
        private String subject;
        private String body;
        private String htmlBody;
        private String category;
        private String fromEmail;
        private String fromName;
        private String replyTo;

        public Builder name(String name) { this.name = name; return this; }
        public Builder subject(String subject) { this.subject = subject; return this; }
        public Builder body(String body) { this.body = body; return this; }
        public Builder htmlBody(String htmlBody) { this.htmlBody = htmlBody; return this; }
        public Builder category(String category) { this.category = category; return this; }
        public Builder fromEmail(String fromEmail) { this.fromEmail = fromEmail; return this; }
        public Builder fromName(String fromName) { this.fromName = fromName; return this; }
        public Builder replyTo(String replyTo) { this.replyTo = replyTo; return this; }

        public CreateEmailTemplateCommand build() {
            return new CreateEmailTemplateCommand(name, subject, body, htmlBody, category, fromEmail, fromName, replyTo);
        }
    }
}

package tech.kayys.numou.crm.application.api.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.crm.domain.identifier.CampaignId;
import tech.kayys.numou.crm.domain.identifier.EmailMessageId;

import java.util.Map;

/**
 * Command to send a campaign email.
 */
public record SendCampaignEmailCommand(
        EmailMessageId messageId,
        CampaignId campaignId,
        String email,
        Map<String, String> variables,
        String templateId
) implements Command {

    public SendCampaignEmailCommand {
        if (campaignId == null) {
            throw new IllegalArgumentException("Campaign ID cannot be null");
        }
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email cannot be empty");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private EmailMessageId messageId;
        private CampaignId campaignId;
        private String email;
        private Map<String, String> variables;
        private String templateId;

        public Builder messageId(EmailMessageId messageId) {
            this.messageId = messageId;
            return this;
        }

        public Builder campaignId(CampaignId campaignId) {
            this.campaignId = campaignId;
            return this;
        }

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder variables(Map<String, String> variables) {
            this.variables = variables;
            return this;
        }

        public Builder templateId(String templateId) {
            this.templateId = templateId;
            return this;
        }

        public SendCampaignEmailCommand build() {
            if (messageId == null) {
                messageId = EmailMessageId.generate();
            }
            return new SendCampaignEmailCommand(
                messageId, campaignId, email, variables, templateId
            );
        }
    }
}
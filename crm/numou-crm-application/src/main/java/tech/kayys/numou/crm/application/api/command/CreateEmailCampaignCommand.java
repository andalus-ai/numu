package tech.kayys.numou.crm.application.api.command;

import tech.kayys.numou.foundation.application.command.Command;

import java.time.Instant;
import java.util.List;

public record CreateEmailCampaignCommand(
        String name,
        String subject,
        String templateId,
        List<String> recipientGroups,
        Instant scheduledAt
) implements Command {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String name;
        private String subject;
        private String templateId;
        private List<String> recipientGroups;
        private Instant scheduledAt;

        public Builder name(String name) { this.name = name; return this; }
        public Builder subject(String subject) { this.subject = subject; return this; }
        public Builder templateId(String templateId) { this.templateId = templateId; return this; }
        public Builder recipientGroups(List<String> recipientGroups) { this.recipientGroups = recipientGroups; return this; }
        public Builder scheduledAt(Instant scheduledAt) { this.scheduledAt = scheduledAt; return this; }

        public CreateEmailCampaignCommand build() {
            return new CreateEmailCampaignCommand(name, subject, templateId, recipientGroups, scheduledAt);
        }
    }
}

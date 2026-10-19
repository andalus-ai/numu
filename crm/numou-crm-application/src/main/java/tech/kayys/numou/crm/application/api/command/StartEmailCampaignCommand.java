package tech.kayys.numou.crm.application.api.command;

import tech.kayys.numou.crm.domain.identifier.CampaignId;
import tech.kayys.numou.foundation.application.command.Command;

public record StartEmailCampaignCommand(
        CampaignId campaignId
) implements Command {
}

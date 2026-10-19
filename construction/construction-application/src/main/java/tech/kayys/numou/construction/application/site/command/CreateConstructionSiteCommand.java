package tech.kayys.numou.construction.application.site.command;

import tech.kayys.numou.construction.domain.site.SiteAddress;
import tech.kayys.numou.construction.domain.site.SiteType;
import tech.kayys.numou.foundation.application.command.Command;
import java.util.UUID;

public record CreateConstructionSiteCommand(
        UUID projectId,
        String siteCode,
        String name,
        SiteType type,
        SiteAddress address,
        Double latitude,
        Double longitude,
        String timezone
) implements Command {}

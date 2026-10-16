package tech.kayys.syirkah.construction.application.site.command;

import tech.kayys.syirkah.construction.domain.site.SiteAddress;
import tech.kayys.syirkah.construction.domain.site.SiteType;
import tech.kayys.syirkah.foundation.application.command.Command;
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

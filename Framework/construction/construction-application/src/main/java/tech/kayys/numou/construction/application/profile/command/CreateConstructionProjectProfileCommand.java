package tech.kayys.syirkah.construction.application.profile.command;

import tech.kayys.syirkah.construction.domain.profile.ConstructionContractType;
import tech.kayys.syirkah.construction.domain.profile.ConstructionType;
import tech.kayys.syirkah.construction.domain.profile.DeliveryMethod;
import tech.kayys.syirkah.foundation.application.command.Command;
import java.util.UUID;

public record CreateConstructionProjectProfileCommand(
        UUID projectId,
        ConstructionType constructionType,
        DeliveryMethod deliveryMethod,
        ConstructionContractType contractType,
        String description
) implements Command {}

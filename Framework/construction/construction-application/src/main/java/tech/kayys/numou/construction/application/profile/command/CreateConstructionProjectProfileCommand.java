package tech.kayys.numou.construction.application.profile.command;

import tech.kayys.numou.construction.domain.profile.ConstructionContractType;
import tech.kayys.numou.construction.domain.profile.ConstructionType;
import tech.kayys.numou.construction.domain.profile.DeliveryMethod;
import tech.kayys.numou.foundation.application.command.Command;
import java.util.UUID;

public record CreateConstructionProjectProfileCommand(
        UUID projectId,
        ConstructionType constructionType,
        DeliveryMethod deliveryMethod,
        ConstructionContractType contractType,
        String description
) implements Command {}

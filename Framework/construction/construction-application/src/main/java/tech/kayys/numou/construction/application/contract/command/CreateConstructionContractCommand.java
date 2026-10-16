package tech.kayys.numou.construction.application.contract.command;

import tech.kayys.numou.construction.domain.contract.ContractParty;
import tech.kayys.numou.construction.domain.contract.ContractValue;
import tech.kayys.numou.foundation.application.command.Command;
import java.util.UUID;

public record CreateConstructionContractCommand(
        UUID projectId,
        String contractNumber,
        String title,
        ContractParty client,
        ContractParty contractor,
        ContractValue originalValue
) implements Command {}

package tech.kayys.syirkah.construction.application.contract.command;

import tech.kayys.syirkah.construction.domain.contract.ContractParty;
import tech.kayys.syirkah.construction.domain.contract.ContractValue;
import tech.kayys.syirkah.foundation.application.command.Command;
import java.util.UUID;

public record CreateConstructionContractCommand(
        UUID projectId,
        String contractNumber,
        String title,
        ContractParty client,
        ContractParty contractor,
        ContractValue originalValue
) implements Command {}

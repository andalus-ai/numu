package tech.kayys.syirkah.construction.spi.closeout;

import tech.kayys.syirkah.construction.domain.closeout.FinalAccount;
import tech.kayys.syirkah.construction.domain.closeout.FinalAccountId;
import tech.kayys.syirkah.foundation.domain.repository.Repository;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface FinalAccountRepository extends Repository<FinalAccount, FinalAccountId> {
    CompletionStage<Optional<FinalAccount>> findByContractId(UUID contractId);
}

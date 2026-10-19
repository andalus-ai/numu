package tech.kayys.numou.construction.spi.closeout;

import tech.kayys.numou.construction.domain.closeout.FinalAccount;
import tech.kayys.numou.construction.domain.closeout.FinalAccountId;
import tech.kayys.numou.foundation.domain.repository.Repository;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface FinalAccountRepository extends Repository<FinalAccount, FinalAccountId> {
    CompletionStage<Optional<FinalAccount>> findByContractId(UUID contractId);
}

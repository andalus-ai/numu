package tech.kayys.numou.budget.spi.port;

import tech.kayys.numou.budget.domain.*;

import java.util.List;
import java.util.Optional;

public interface CommitmentRepository {
    BudgetCommitment save(BudgetCommitment commitment);
    Optional<BudgetCommitment> find(CommitmentId id);
    List<BudgetCommitment> openFor(BudgetId budgetId, String accountCode, BudgetPeriod period);
}

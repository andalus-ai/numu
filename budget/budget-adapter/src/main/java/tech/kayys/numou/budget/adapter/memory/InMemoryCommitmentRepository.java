package tech.kayys.numou.budget.adapter.memory;

import tech.kayys.numou.budget.spi.port.CommitmentRepository;
import tech.kayys.numou.budget.domain.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryCommitmentRepository implements CommitmentRepository {
    private final Map<CommitmentId, BudgetCommitment> commitments = new ConcurrentHashMap<>();
    @Override public BudgetCommitment save(BudgetCommitment commitment) { commitments.put(commitment.id(), commitment); return commitment; }
    @Override public Optional<BudgetCommitment> find(CommitmentId id) { return Optional.ofNullable(commitments.get(id)); }
    @Override public List<BudgetCommitment> openFor(BudgetId budgetId, String accountCode, BudgetPeriod period) {
        return commitments.values().stream()
                .filter(c -> c.budgetId().equals(budgetId) && c.accountCode().equals(accountCode)
                        && c.period().equals(period)
                        && c.status() != CommitmentStatus.RELEASED && c.status() != CommitmentStatus.CONSUMED)
                .toList();
    }
}

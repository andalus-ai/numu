package tech.kayys.numou.budget.adapter.memory;

import tech.kayys.numou.budget.spi.port.CommitmentRepository;
import tech.kayys.numou.budget.domain.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryCommitmentRepository implements CommitmentRepository {
    private final Map<String, BudgetCommitment> commitments = new ConcurrentHashMap<>();
    @Override public BudgetCommitment save(BudgetCommitment commitment) { commitments.put(key(commitment.tenantId(), commitment.id()), commitment); return commitment; }
    @Override public Optional<BudgetCommitment> find(String tenantId, CommitmentId id) { return Optional.ofNullable(commitments.get(key(tenantId, id))); }
    @Override public List<BudgetCommitment> openFor(String tenantId, BudgetId budgetId, String accountCode, BudgetPeriod period) {
        return commitments.values().stream()
                .filter(c -> c.tenantId().equals(tenantId) && c.budgetId().equals(budgetId) && c.accountCode().equals(accountCode)
                        && c.period().equals(period)
                        && c.status() != CommitmentStatus.RELEASED && c.status() != CommitmentStatus.CONSUMED)
                .toList();
    }
    private String key(String tenantId, CommitmentId id) { return tenantId + "|" + id.value(); }
}

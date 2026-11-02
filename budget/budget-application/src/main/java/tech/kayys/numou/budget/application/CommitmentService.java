package tech.kayys.numou.budget.application;

import tech.kayys.numou.budget.application.control.BudgetControlEngine;
import tech.kayys.numou.budget.spi.port.CommitmentRepository;
import tech.kayys.numou.budget.domain.*;
import java.math.BigDecimal;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

public final class CommitmentService {
    private final CommitmentRepository commitments;
    private final BudgetControlEngine control;
    public CommitmentService(CommitmentRepository commitments, BudgetControlEngine control) {
        this.commitments = Objects.requireNonNull(commitments); this.control = Objects.requireNonNull(control);
    }
    public BudgetCommitment create(String tenantId, CommitmentId id, BudgetId budgetId, String accountCode,
                                   Map<String, String> dimensions, BudgetPeriod period,
                                   BigDecimal amount, String reference) {
        requireTenant(tenantId);
        if (commitments.find(tenantId, id).isPresent()) throw new BudgetViolationException("commitment already exists: " + id);
        if (control.check(tenantId, budgetId, accountCode, dimensions, period, amount).outcome() == BudgetControlOutcome.BLOCK) {
            throw new InsufficientBudgetException("requested amount exceeds available budget");
        }
        return commitments.save(new BudgetCommitment(tenantId, id, budgetId, accountCode, period, amount, reference));
    }
    public BudgetCommitment consume(String tenantId, CommitmentId id, BigDecimal amount) {
        var commitment = require(tenantId, id); commitment.consume(amount); return commitments.save(commitment);
    }
    public BudgetCommitment release(String tenantId, CommitmentId id) {
        var commitment = require(tenantId, id); commitment.release(); return commitments.save(commitment);
    }
    private void requireTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
    }
    private BudgetCommitment require(String tenantId, CommitmentId id) {
        requireTenant(tenantId);
        return commitments.find(tenantId, id).orElseThrow(() -> new NoSuchElementException("unknown commitment: " + id));
    }
}

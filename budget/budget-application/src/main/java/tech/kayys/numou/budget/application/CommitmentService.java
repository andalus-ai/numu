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
    public BudgetCommitment create(CommitmentId id, BudgetId budgetId, String accountCode,
                                   Map<String, String> dimensions, BudgetPeriod period,
                                   BigDecimal amount, String reference) {
        if (commitments.find(id).isPresent()) throw new BudgetViolationException("commitment already exists: " + id);
        if (control.check(budgetId, accountCode, dimensions, period, amount).outcome() == BudgetControlOutcome.BLOCK) {
            throw new InsufficientBudgetException("requested amount exceeds available budget");
        }
        return commitments.save(new BudgetCommitment(id, budgetId, accountCode, period, amount, reference));
    }
    public BudgetCommitment consume(CommitmentId id, BigDecimal amount) {
        var commitment = require(id); commitment.consume(amount); return commitments.save(commitment);
    }
    public BudgetCommitment release(CommitmentId id) {
        var commitment = require(id); commitment.release(); return commitments.save(commitment);
    }
    private BudgetCommitment require(CommitmentId id) {
        return commitments.find(id).orElseThrow(() -> new NoSuchElementException("unknown commitment: " + id));
    }
}

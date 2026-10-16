package tech.kayys.syirkah.accounting.application.close;

import tech.kayys.syirkah.accounting.domain.close.CloseCycle;
import tech.kayys.syirkah.accounting.domain.close.CloseCycleId;
import tech.kayys.syirkah.accounting.domain.close.CloseTask;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Application service orchestrating period-end financial close workflows.
 */
public final class FinancialCloseService {

    private final Map<CloseCycleId, CloseCycle> closeCycles = new ConcurrentHashMap<>();
    private final CloseValidationEngine validationEngine;
    private final PeriodLockEngine lockEngine;

    public FinancialCloseService(CloseValidationEngine validationEngine, PeriodLockEngine lockEngine) {
        this.validationEngine = Objects.requireNonNull(validationEngine, "validationEngine must not be null");
        this.lockEngine = Objects.requireNonNull(lockEngine, "lockEngine must not be null");
    }

    public CloseCycle openCycle(CloseCycleId id, String tenantId, String ledgerId, String fiscalPeriodId) {
        CloseCycle cycle = CloseCycle.open(id, tenantId, ledgerId, fiscalPeriodId);
        closeCycles.put(cycle.id(), cycle);
        return cycle;
    }

    public void completeTask(CloseCycleId cycleId, String taskCode, String user) {
        CloseCycle cycle = load(cycleId);
        CloseTask task = cycle.findTask(taskCode)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskCode));
        task.complete(user);
    }

    public CloseValidationEngine.ValidationResult beginValidation(CloseCycleId cycleId) {
        CloseCycle cycle = load(cycleId);
        cycle.beginValidation();
        return validationEngine.validate(cycle);
    }

    public CloseCycle approveAndLock(CloseCycleId cycleId, String approver) {
        CloseCycle cycle = load(cycleId);
        cycle.approve(approver);
        cycle.lock();
        lockEngine.lockPeriod(cycle.tenantId(), cycle.ledgerId(), cycle.fiscalPeriodId());
        return cycle;
    }

    public Optional<CloseCycle> findById(CloseCycleId id) {
        return Optional.ofNullable(closeCycles.get(id));
    }

    private CloseCycle load(CloseCycleId id) {
        return findById(id).orElseThrow(() -> new IllegalArgumentException("CloseCycle not found: " + id.value()));
    }
}

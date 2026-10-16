package tech.kayys.numou.asset.domain.maintenance.plan;

import tech.kayys.numou.asset.domain.event.maintenance.MaintenancePlanActivated;
import tech.kayys.numou.asset.domain.event.maintenance.MaintenancePlanCreated;
import tech.kayys.numou.asset.domain.event.maintenance.MaintenancePlanRetired;
import tech.kayys.numou.asset.domain.event.maintenance.MaintenancePlanSuspended;
import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.foundation.domain.exception.InvalidStateException;
import tech.kayys.numou.foundation.domain.time.DomainClock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Maintenance plan aggregate: defines <em>what</em> maintenance should happen (ASSET-22 §4).
 *
 * <p>Plans are reusable across assets; per-asset binding lives in the schedule.</p>
 */
public final class MaintenancePlan extends AbstractAggregateRoot<MaintenancePlanId> {

    private static final long serialVersionUID = 1L;

    private String tenantId;
    private String planNumber;
    private String name;
    private String description;
    private MaintenancePlanStatus status;
    private final List<MaintenanceRule> rules = new ArrayList<>();

    private MaintenancePlan() { super(); }

    private MaintenancePlan(MaintenancePlanId id, String tenantId, String planNumber, String name) {
        super(id);
        this.tenantId = requireText(tenantId, "tenantId");
        this.planNumber = requireText(planNumber, "planNumber");
        this.name = requireText(name, "name");
        this.status = MaintenancePlanStatus.DRAFT;
    }

    public static MaintenancePlan create(MaintenancePlanId id, String tenantId, String planNumber,
                                         String name, String description, List<MaintenanceRule> rules,
                                         DomainClock clock) {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(clock, "clock cannot be null");
        MaintenancePlan plan = new MaintenancePlan(id, tenantId, planNumber, name);
        plan.description = description;
        if (rules != null) {
            for (MaintenanceRule rule : rules) {
                plan.addRule(rule);
            }
        }
        if (plan.rules.isEmpty()) {
            throw new BusinessRuleViolation("maintenance plan must define at least one rule");
        }
        plan.setCreatedAt(clock.now());
        plan.setUpdatedAt(clock.now());
        plan.raise(new MaintenancePlanCreated(UUID.randomUUID(), clock.now(), id.value(), plan.planNumber));
        return plan;
    }

    public static MaintenancePlan reconstitute(MaintenancePlanId id, String tenantId, String planNumber,
                                               String name, String description, MaintenancePlanStatus status,
                                               List<MaintenanceRule> rules) {
        MaintenancePlan plan = new MaintenancePlan(id, tenantId, planNumber, name);
        plan.description = description;
        plan.status = Objects.requireNonNull(status, "status cannot be null");
        if (rules != null) {
            plan.rules.addAll(rules);
        }
        return plan;
    }

    public void addRule(MaintenanceRule rule) {
        Objects.requireNonNull(rule, "rule cannot be null");
        if (status == MaintenancePlanStatus.RETIRED) {
            throw new InvalidStateException("Retired plan cannot accept new rules");
        }
        rules.add(rule);
    }

    public void activate(DomainClock clock) {
        Objects.requireNonNull(clock, "clock cannot be null");
        if (status != MaintenancePlanStatus.DRAFT && status != MaintenancePlanStatus.SUSPENDED) {
            throw new InvalidStateException("Maintenance plan cannot transition from " + status + " to ACTIVE");
        }
        if (rules.stream().noneMatch(MaintenanceRule::active)) {
            throw new BusinessRuleViolation("maintenance plan must have at least one active rule");
        }
        status = MaintenancePlanStatus.ACTIVE;
        touch(clock);
        raise(new MaintenancePlanActivated(UUID.randomUUID(), clock.now(), id.value()));
    }

    public void suspend(DomainClock clock) {
        Objects.requireNonNull(clock, "clock cannot be null");
        if (status != MaintenancePlanStatus.ACTIVE) {
            throw new InvalidStateException("Only ACTIVE plans can be suspended (was " + status + ")");
        }
        status = MaintenancePlanStatus.SUSPENDED;
        touch(clock);
        raise(new MaintenancePlanSuspended(UUID.randomUUID(), clock.now(), id.value()));
    }

    public void retire(DomainClock clock) {
        Objects.requireNonNull(clock, "clock cannot be null");
        if (status == MaintenancePlanStatus.RETIRED) {
            throw new InvalidStateException("Maintenance plan is already retired");
        }
        status = MaintenancePlanStatus.RETIRED;
        touch(clock);
        raise(new MaintenancePlanRetired(UUID.randomUUID(), clock.now(), id.value()));
    }

    private void touch(DomainClock clock) {
        setUpdatedAt(clock.now());
        incrementVersion();
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " cannot be null");
        String normalized = value.trim();
        if (normalized.isBlank()) {
            throw new BusinessRuleViolation(field + " cannot be blank");
        }
        return normalized;
    }

    public String tenantId() { return tenantId; }
    public String planNumber() { return planNumber; }
    public String name() { return name; }
    public String description() { return description; }
    public MaintenancePlanStatus status() { return status; }
    public List<MaintenanceRule> rules() { return Collections.unmodifiableList(rules); }
    public List<MaintenanceRule> activeRules() {
        return rules.stream().filter(MaintenanceRule::active).toList();
    }
}

package tech.kayys.numou.workforce.domain.compensation;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.workforce.domain.compensation.event.CompensationTermsCreated;
import tech.kayys.numou.workforce.domain.compensation.event.CompensationTermsEnded;
import tech.kayys.numou.workforce.domain.employment.EmploymentId;
import tech.kayys.numou.workforce.domain.paycomponent.PayComponentId;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * CompensationTerms aggregate root.
 *
 * <p>Represents the agreed compensation terms for an Employment.
 * Always effective-dated and versioned to prevent overwriting historical compensation.
 */
public final class CompensationTerms extends AbstractAggregateRoot<CompensationTermsId> {

    private final EmploymentId employmentId;
    private final Money basePay;
    private final PayFrequency payFrequency;
    private final List<CompensationComponent> components;
    private final LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private CompensationTermsStatus status;

    private CompensationTerms(
            CompensationTermsId id,
            EmploymentId employmentId,
            Money basePay,
            PayFrequency payFrequency,
            LocalDate effectiveFrom
    ) {
        super(id);
        this.employmentId = Objects.requireNonNull(employmentId, "employmentId must not be null");
        this.basePay = Objects.requireNonNull(basePay, "basePay must not be null");
        if (basePay.isNegative()) {
            throw new IllegalArgumentException("Base pay cannot be negative");
        }
        this.payFrequency = Objects.requireNonNull(payFrequency, "payFrequency must not be null");
        this.effectiveFrom = Objects.requireNonNull(effectiveFrom, "effectiveFrom must not be null");
        this.status = CompensationTermsStatus.ACTIVE;
        this.components = new ArrayList<>();
    }

    public static CompensationTerms create(
            CompensationTermsId id,
            EmploymentId employmentId,
            Money basePay,
            PayFrequency payFrequency,
            LocalDate effectiveFrom
    ) {
        CompensationTerms terms = new CompensationTerms(id, employmentId, basePay, payFrequency, effectiveFrom);
        terms.raise(new CompensationTermsCreated(id, employmentId, basePay, payFrequency, effectiveFrom));
        return terms;
    }

    public void addComponent(CompensationComponent component) {
        Objects.requireNonNull(component, "component must not be null");
        if (status != CompensationTermsStatus.ACTIVE) {
            throw new IllegalStateException("Cannot add component to non-active compensation terms");
        }
        components.removeIf(c -> c.componentId().equals(component.componentId()));
        components.add(component);
    }

    public void removeComponent(PayComponentId componentId) {
        Objects.requireNonNull(componentId, "componentId must not be null");
        if (status != CompensationTermsStatus.ACTIVE) {
            throw new IllegalStateException("Cannot remove component from non-active compensation terms");
        }
        components.removeIf(c -> c.componentId().equals(componentId));
    }

    public void end(LocalDate effectiveTo) {
        Objects.requireNonNull(effectiveTo, "effectiveTo must not be null");
        if (effectiveTo.isBefore(effectiveFrom)) {
            throw new IllegalArgumentException("End date cannot precede effective date");
        }
        this.effectiveTo = effectiveTo;
        this.status = CompensationTermsStatus.ENDED;
        raise(new CompensationTermsEnded(getId(), employmentId, effectiveTo));
    }

    public boolean isEffectiveOn(LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");
        if (status != CompensationTermsStatus.ACTIVE) {
            return false;
        }
        if (date.isBefore(effectiveFrom)) {
            return false;
        }
        return effectiveTo == null || !date.isAfter(effectiveTo);
    }

    public EmploymentId employmentId() {
        return employmentId;
    }

    public Money basePay() {
        return basePay;
    }

    public PayFrequency payFrequency() {
        return payFrequency;
    }

    public List<CompensationComponent> components() {
        return Collections.unmodifiableList(components);
    }

    public LocalDate effectiveFrom() {
        return effectiveFrom;
    }

    public LocalDate effectiveTo() {
        return effectiveTo;
    }

    public CompensationTermsStatus status() {
        return status;
    }
}

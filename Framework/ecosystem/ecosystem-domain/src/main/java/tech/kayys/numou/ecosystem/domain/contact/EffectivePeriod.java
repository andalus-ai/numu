package tech.kayys.syirkah.ecosystem.domain.contact;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Date-range validity period with inclusivity, containment, and overlap evaluation (config03.md §P4-13).
 */
public record EffectivePeriod(LocalDate validFrom, LocalDate validTo) implements Serializable {

    public EffectivePeriod {
        Objects.requireNonNull(validFrom, "validFrom cannot be null");
        if (validTo != null && validTo.isBefore(validFrom)) {
            throw new IllegalArgumentException("validTo cannot precede validFrom");
        }
    }

    public static EffectivePeriod openEnded(LocalDate from) {
        return new EffectivePeriod(from, null);
    }

    public static EffectivePeriod of(LocalDate from, LocalDate to) {
        return new EffectivePeriod(from, to);
    }

    public boolean contains(LocalDate date) {
        if (date == null) {
            return false;
        }
        if (date.isBefore(validFrom)) {
            return false;
        }
        return validTo == null || !date.isAfter(validTo);
    }

    public boolean isEffective(LocalDate date) {
        return contains(date);
    }

    public boolean isEffectiveNow() {
        return contains(LocalDate.now());
    }

    public boolean overlaps(EffectivePeriod other) {
        if (other == null) {
            return false;
        }
        LocalDate otherStart = other.validFrom();
        LocalDate otherEnd = other.validTo();

        boolean thisStartsAfterOtherEnds = (otherEnd != null && this.validFrom.isAfter(otherEnd));
        boolean otherStartsAfterThisEnds = (this.validTo != null && otherStart.isAfter(this.validTo));

        return !thisStartsAfterOtherEnds && !otherStartsAfterThisEnds;
    }
}

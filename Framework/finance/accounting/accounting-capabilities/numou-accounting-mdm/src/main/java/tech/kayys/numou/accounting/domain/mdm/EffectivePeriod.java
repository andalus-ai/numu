package tech.kayys.syirkah.accounting.domain.mdm;

import java.time.LocalDate;
import java.util.Objects;

/** Date-range validity period for effective-dated master records. */
public record EffectivePeriod(LocalDate validFrom, LocalDate validTo) {
    public EffectivePeriod {
        Objects.requireNonNull(validFrom, "validFrom");
        if (validTo != null && validTo.isBefore(validFrom)) {
            throw new IllegalArgumentException("validTo cannot precede validFrom");
        }
    }
    public static EffectivePeriod openEnded(LocalDate from) { return new EffectivePeriod(from, null); }

    public boolean contains(LocalDate date) {
        if (date.isBefore(validFrom)) return false;
        return validTo == null || !date.isAfter(validTo);
    }
}

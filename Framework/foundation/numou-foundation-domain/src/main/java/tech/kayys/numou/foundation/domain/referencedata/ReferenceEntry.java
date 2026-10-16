package tech.kayys.numou.foundation.domain.referencedata;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Controlled vocabulary entry separating stable identity/code from display label and lifecycle (config03.md §P4-16 #4).
 */
public record ReferenceEntry(
        ReferenceEntryId id,
        ReferenceCode code,
        String label,
        String description,
        ReferenceEntryStatus status,
        LocalDate validFrom,
        LocalDate validTo,
        String source,
        long version
) implements Serializable {

    public ReferenceEntry {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(code, "code cannot be null");
        Objects.requireNonNull(label, "label cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        if (validFrom != null && validTo != null && validTo.isBefore(validFrom)) {
            throw new IllegalArgumentException("validTo cannot precede validFrom");
        }
    }

    public boolean isEffective(LocalDate date) {
        if (date == null) {
            return true;
        }
        if (validFrom != null && date.isBefore(validFrom)) {
            return false;
        }
        return validTo == null || !date.isAfter(validTo);
    }

    public boolean isUsable(LocalDate date) {
        return status == ReferenceEntryStatus.ACTIVE && isEffective(date);
    }
}

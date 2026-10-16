package tech.kayys.syirkah.accounting.domain.legal;

import java.util.Objects;
import java.util.UUID;

public record ComplianceCalendarEntryId(String value) {
    public ComplianceCalendarEntryId {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("ComplianceCalendarEntryId must not be blank");
    }
    public static ComplianceCalendarEntryId newId() {
        return new ComplianceCalendarEntryId(UUID.randomUUID().toString());
    }
    public static ComplianceCalendarEntryId of(String value) {
        return new ComplianceCalendarEntryId(value);
    }
}

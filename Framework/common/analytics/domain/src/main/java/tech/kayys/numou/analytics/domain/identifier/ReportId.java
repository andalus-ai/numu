package tech.kayys.syirkah.analytics.domain.identifier;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

/**
 * Report identifier.
 */
public record ReportId(UUID value) implements DomainId<UUID>, Serializable {

    public ReportId {
        Objects.requireNonNull(value, "ReportId value cannot be null");
    }

    public static ReportId of(UUID value) {
        return new ReportId(value);
    }

    public static ReportId generate() {
        return new ReportId(UUID.randomUUID());
    }

    public static ReportId fromString(String value) {
        return new ReportId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "ReportId{" + value + "}";
    }
}
package tech.kayys.syirkah.accounting.domain.maintenance;

import java.util.Objects;
import java.util.UUID;

public record MeterReadingId(String value) {
    public MeterReadingId {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("MeterReadingId must not be blank");
    }
    public static MeterReadingId newId() {
        return new MeterReadingId(UUID.randomUUID().toString());
    }
    public static MeterReadingId of(String value) {
        return new MeterReadingId(value);
    }
}

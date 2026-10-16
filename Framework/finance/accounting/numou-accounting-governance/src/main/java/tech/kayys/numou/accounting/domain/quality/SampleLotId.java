package tech.kayys.syirkah.accounting.domain.quality;

import java.util.Objects;
import java.util.UUID;

public record SampleLotId(String value) {
    public SampleLotId {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("SampleLotId must not be blank");
    }
    public static SampleLotId newId() {
        return new SampleLotId(UUID.randomUUID().toString());
    }
    public static SampleLotId of(String value) {
        return new SampleLotId(value);
    }
}

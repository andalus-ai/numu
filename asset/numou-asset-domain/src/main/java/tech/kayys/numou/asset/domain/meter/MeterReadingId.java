package tech.kayys.numou.asset.domain.meter;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

/** Reading identity (ASSET-21 §21.8). */
public record MeterReadingId(UUID value) implements DomainId<UUID>, Serializable {

    public MeterReadingId {
        Objects.requireNonNull(value, "MeterReadingId value cannot be null");
    }

    public static MeterReadingId of(UUID value) {
        return new MeterReadingId(value);
    }

    public static MeterReadingId generate() {
        return new MeterReadingId(UUID.randomUUID());
    }

    public static MeterReadingId fromString(String value) {
        return new MeterReadingId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "MeterReadingId{" + value + "}";
    }
}

package tech.kayys.syirkah.accounting.domain.maintenance;

import java.time.Instant;
import java.util.Objects;

public final class MeterReading {
    private final MeterReadingId id;
    private final String assetId;
    private final MeterUnit unit;
    private final double value;
    private final Instant readingTime;
    private final String recordedBy;

    public MeterReading(MeterReadingId id, String assetId, MeterUnit unit, double value, String recordedBy) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.assetId = Objects.requireNonNull(assetId, "assetId must not be null");
        this.unit = Objects.requireNonNull(unit, "unit must not be null");
        this.value = value;
        this.readingTime = Instant.now();
        this.recordedBy = Objects.requireNonNull(recordedBy, "recordedBy must not be null");
    }

    public MeterReadingId id() { return id; }
    public String assetId() { return assetId; }
    public MeterUnit unit() { return unit; }
    public double value() { return value; }
    public Instant readingTime() { return readingTime; }
    public String recordedBy() { return recordedBy; }
}

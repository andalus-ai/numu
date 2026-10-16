package tech.kayys.syirkah.accounting.domain.quality;

import java.time.LocalDate;
import java.util.Objects;

public final class SampleLot {
    private final SampleLotId id;
    private final String itemCode;
    private final String batchNumber;
    private final int sampleSize;
    private final LocalDate sampledDate;
    private SampleLotStatus status;
    private String inspectionId;

    public SampleLot(SampleLotId id, String itemCode, String batchNumber, int sampleSize) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.itemCode = Objects.requireNonNull(itemCode, "itemCode must not be null");
        this.batchNumber = Objects.requireNonNull(batchNumber, "batchNumber must not be null");
        this.sampleSize = Math.max(1, sampleSize);
        this.sampledDate = LocalDate.now();
        this.status = SampleLotStatus.SAMPLED;
    }

    public void linkInspection(String inspectionId) {
        this.inspectionId = Objects.requireNonNull(inspectionId, "inspectionId must not be null");
        this.status = SampleLotStatus.INSPECTED;
    }

    public void release() {
        this.status = SampleLotStatus.RELEASED;
    }

    public void reject() {
        this.status = SampleLotStatus.REJECTED;
    }

    public void quarantine() {
        this.status = SampleLotStatus.QUARANTINED;
    }

    public SampleLotId id() { return id; }
    public String itemCode() { return itemCode; }
    public String batchNumber() { return batchNumber; }
    public int sampleSize() { return sampleSize; }
    public LocalDate sampledDate() { return sampledDate; }
    public SampleLotStatus status() { return status; }
    public String inspectionId() { return inspectionId; }
}

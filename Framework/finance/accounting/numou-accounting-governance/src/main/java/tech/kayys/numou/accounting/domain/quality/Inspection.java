package tech.kayys.syirkah.accounting.domain.quality;

import java.time.Instant;
import java.util.Objects;

public final class Inspection {
    public enum Status { PENDING, PASSED, FAILED }

    private final InspectionId id;
    private final String itemReference;
    private final String inspector;
    private Status status;
    private String notes;
    private final Instant createdAt;

    public Inspection(InspectionId id, String itemReference, String inspector) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.itemReference = Objects.requireNonNull(itemReference, "itemReference must not be null");
        this.inspector = Objects.requireNonNull(inspector, "inspector must not be null");
        this.status = Status.PENDING;
        this.createdAt = Instant.now();
    }

    public void pass(String notes) {
        this.status = Status.PASSED;
        this.notes = notes;
    }

    public void fail(String notes) {
        this.status = Status.FAILED;
        this.notes = notes;
    }

    public void pass() { pass(""); }
    public void fail() { fail(""); }

    public InspectionId id() { return id; }
    public String itemReference() { return itemReference; }
    public String lotNumber() { return itemReference; }
    public String itemId() { return itemReference; }
    public String inspector() { return inspector; }
    public Status status() { return status; }
    public String notes() { return notes; }
    public Instant createdAt() { return createdAt; }
}

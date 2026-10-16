package tech.kayys.syirkah.accounting.domain.maintenance;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class MaintenanceWorkOrder {
    public enum Status { DRAFT, SCHEDULED, APPROVED, IN_PROGRESS, COMPLETED, CLOSED, CANCELLED }
    public enum Priority { LOW, MEDIUM, HIGH, URGENT }

    private final String id;
    private final String assetId;
    private final String description;
    private final Priority priority;
    private Status status;
    private BigDecimal actualCost;
    private final List<WorkOrderPart> parts = new ArrayList<>();
    private final List<WorkOrderLabor> labor = new ArrayList<>();
    private boolean capitalizable;

    public MaintenanceWorkOrder(String id, String assetId, String description, Priority priority) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.assetId = Objects.requireNonNull(assetId, "assetId must not be null");
        this.description = Objects.requireNonNull(description, "description must not be null");
        this.priority = Objects.requireNonNull(priority, "priority must not be null");
        this.status = Status.DRAFT;
        this.actualCost = BigDecimal.ZERO;
        this.capitalizable = false;
    }

    public void schedule() {
        this.status = Status.SCHEDULED;
    }

    public void approve() {
        this.status = Status.APPROVED;
    }

    public void start() {
        this.status = Status.IN_PROGRESS;
    }

    public void complete(BigDecimal cost) {
        if (this.status != Status.IN_PROGRESS) {
            throw new IllegalStateException("Cannot complete work order that is not IN_PROGRESS; current: " + this.status);
        }
        this.status = Status.COMPLETED;
        this.actualCost = Objects.requireNonNullElse(cost, BigDecimal.ZERO);
    }

    public void complete() {
        complete(totalActualCost());
    }

    public void close() {
        this.status = Status.CLOSED;
    }

    public void cancel() {
        this.status = Status.CANCELLED;
    }

    public void addPart(WorkOrderPart part) {
        parts.add(Objects.requireNonNull(part, "part must not be null"));
    }

    public void addLabor(WorkOrderLabor lab) {
        labor.add(Objects.requireNonNull(lab, "lab must not be null"));
    }

    public void setCapitalizable(boolean capitalizable) {
        this.capitalizable = capitalizable;
    }

    public BigDecimal totalPartsCost() {
        return parts.stream().map(WorkOrderPart::totalCost).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal totalLaborCost() {
        return labor.stream().map(WorkOrderLabor::totalLaborCost).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal totalActualCost() {
        var calculated = totalPartsCost().add(totalLaborCost());
        return calculated.compareTo(BigDecimal.ZERO) > 0 ? calculated : actualCost;
    }

    public BigDecimal actualCost() {
        return totalActualCost();
    }

    public String id() { return id; }
    public String assetId() { return assetId; }
    public String description() { return description; }
    public Priority priority() { return priority; }
    public Status status() { return status; }
    public boolean isCapitalizable() { return capitalizable; }
    public List<WorkOrderPart> parts() { return List.copyOf(parts); }
    public List<WorkOrderLabor> labor() { return List.copyOf(labor); }
}

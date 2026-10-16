package tech.kayys.syirkah.asset.domain.inspection;

import tech.kayys.syirkah.asset.domain.event.AssetInspectionCancelled;
import tech.kayys.syirkah.asset.domain.event.AssetInspectionCompleted;
import tech.kayys.syirkah.asset.domain.event.AssetInspectionCreated;
import tech.kayys.syirkah.asset.domain.event.AssetInspectionFindingAdded;
import tech.kayys.syirkah.asset.domain.event.AssetInspectionStarted;
import tech.kayys.syirkah.asset.domain.inspection.finding.FindingSeverity;
import tech.kayys.syirkah.asset.domain.inspection.finding.InspectionFinding;
import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.syirkah.foundation.domain.exception.InvalidStateException;
import tech.kayys.syirkah.foundation.domain.time.DomainClock;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Inspection aggregate root (ASSET-20 20.8-20.11). References asset by id only. */
public final class AssetInspection extends AbstractAggregateRoot<AssetInspectionId> {

    private static final long serialVersionUID = 1L;

    private String tenantId;
    private UUID assetId;
    private String inspectionNumber;
    private InspectionType type;
    private AssetInspectionStatus status;
    private AssetCondition overallCondition;
    private InspectionResult result;
    private String inspectorId;
    private String notes;
    private UUID workOrderId;
    private Instant scheduledFor;
    private Instant startedAt;
    private Instant completedAt;
    private Instant cancelledAt;
    private final List<InspectionItem> items = new ArrayList<>();
    private final List<InspectionFinding> findings = new ArrayList<>();

    private AssetInspection() { super(); }

    private AssetInspection(AssetInspectionId id, String tenantId, UUID assetId, String inspectionNumber,
            InspectionType type, String inspectorId, String notes, UUID workOrderId, Instant scheduledFor) {
        super(id);
        this.tenantId = text(tenantId, "tenantId");
        this.assetId = Objects.requireNonNull(assetId, "assetId cannot be null");
        this.inspectionNumber = text(inspectionNumber, "inspectionNumber");
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.status = AssetInspectionStatus.DRAFT;
        this.inspectorId = inspectorId;
        this.notes = notes;
        this.workOrderId = workOrderId;
        this.scheduledFor = scheduledFor;
    }

    public static AssetInspection create(AssetInspectionId id, String tenantId, UUID assetId,
            String inspectionNumber, InspectionType type, String inspectorId, String notes,
            UUID workOrderId, Instant scheduledFor, DomainClock clock) {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(clock, "clock cannot be null");
        AssetInspection i = new AssetInspection(id, tenantId, assetId, inspectionNumber,
                type, inspectorId, notes, workOrderId, scheduledFor);
        Instant now = clock.now();
        i.setCreatedAt(now);
        i.setUpdatedAt(now);
        i.raise(new AssetInspectionCreated(UUID.randomUUID(), now, id.value(), i.assetId, i.type));
        return i;
    }

    public static AssetInspection reconstitute(AssetInspectionId id, String tenantId, UUID assetId,
            String inspectionNumber, InspectionType type, AssetInspectionStatus status,
            AssetCondition overallCondition, InspectionResult result, String inspectorId, String notes,
            UUID workOrderId, Instant scheduledFor, Instant startedAt, Instant completedAt,
            Instant cancelledAt, List<InspectionItem> items, List<InspectionFinding> findings) {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        AssetInspection i = new AssetInspection();
        i.setId(id);
        i.tenantId = text(tenantId, "tenantId");
        i.assetId = Objects.requireNonNull(assetId, "assetId cannot be null");
        i.inspectionNumber = text(inspectionNumber, "inspectionNumber");
        i.type = Objects.requireNonNull(type, "type cannot be null");
        i.status = status;
        i.overallCondition = overallCondition;
        i.result = result;
        i.inspectorId = inspectorId;
        i.notes = notes;
        i.workOrderId = workOrderId;
        i.scheduledFor = scheduledFor;
        i.startedAt = startedAt;
        i.completedAt = completedAt;
        i.cancelledAt = cancelledAt;
        if (items != null) { i.items.addAll(items); }
        if (findings != null) { i.findings.addAll(findings); }
        return i;
    }

    public void start(DomainClock clock) {
        Objects.requireNonNull(clock, "clock cannot be null");
        if (status != AssetInspectionStatus.DRAFT) {
            throw new InvalidStateException("Only DRAFT inspections can be started");
        }
        status = AssetInspectionStatus.IN_PROGRESS;
        startedAt = clock.now();
        touch(clock);
        raise(new AssetInspectionStarted(UUID.randomUUID(), startedAt, id().value(), assetId));
    }

    public void complete(AssetCondition condition, InspectionResult inspectionResult, DomainClock clock) {
        Objects.requireNonNull(clock, "clock cannot be null");
        if (status != AssetInspectionStatus.IN_PROGRESS) {
            throw new InvalidStateException("Only IN_PROGRESS inspections can be completed");
        }
        if (condition == null) {
            throw new BusinessRuleViolation("condition is required to complete an inspection");
        }
        if (inspectionResult == null) {
            throw new BusinessRuleViolation("result is required to complete an inspection");
        }
        this.overallCondition = condition;
        this.result = inspectionResult;
        this.status = AssetInspectionStatus.COMPLETED;
        this.completedAt = clock.now();
        touch(clock);
        raise(new AssetInspectionCompleted(UUID.randomUUID(), completedAt, id().value(),
                assetId, condition, result));
    }

    public void cancel(DomainClock clock) {
        Objects.requireNonNull(clock, "clock cannot be null");
        if (status != AssetInspectionStatus.DRAFT && status != AssetInspectionStatus.IN_PROGRESS) {
            throw new InvalidStateException("Only DRAFT or IN_PROGRESS inspections can be cancelled");
        }
        status = AssetInspectionStatus.CANCELLED;
        cancelledAt = clock.now();
        touch(clock);
        raise(new AssetInspectionCancelled(UUID.randomUUID(), cancelledAt, id().value(), assetId));
    }

    public InspectionItem addItem(String component, String description,
            AssetCondition condition, InspectionResult itemResult, String notes) {
        if (status == AssetInspectionStatus.COMPLETED || status == AssetInspectionStatus.CANCELLED) {
            throw new InvalidStateException("Cannot add items to a " + status + " inspection");
        }
        InspectionItem item = InspectionItem.of(component, description, condition, itemResult, notes);
        items.add(item);
        return item;
    }

    public tech.kayys.syirkah.asset.domain.inspection.finding.InspectionFinding addFinding(
            String category, String description,
            tech.kayys.syirkah.asset.domain.inspection.finding.FindingSeverity severity,
            String recommendedAction, UUID findingWorkOrderId, String recordedBy, DomainClock clock) {
        Objects.requireNonNull(clock, "clock cannot be null");
        if (status == AssetInspectionStatus.COMPLETED || status == AssetInspectionStatus.CANCELLED) {
            throw new InvalidStateException("Cannot add findings to a " + status + " inspection");
        }
        if (description == null || description.isBlank()) {
            throw new BusinessRuleViolation("finding description cannot be blank");
        }
        if (severity == null) {
            throw new BusinessRuleViolation("finding severity is required");
        }
        tech.kayys.syirkah.asset.domain.inspection.finding.InspectionFinding finding =
                tech.kayys.syirkah.asset.domain.inspection.finding.InspectionFinding.record(
                        id(), tenantId, assetId, category, description, severity,
                        recommendedAction, findingWorkOrderId, clock.now(), recordedBy);
        findings.add(finding);
        raise(new AssetInspectionFindingAdded(UUID.randomUUID(), finding.recordedAt(),
                id().value(), assetId, finding.id().value(), severity));
        return finding;
    }

    private void touch(DomainClock clock) {
        setUpdatedAt(clock.now());
        incrementVersion();
    }

    private static String text(String value, String field) {
        Objects.requireNonNull(value, field + " cannot be null");
        String n = value.trim();
        if (n.isBlank()) { throw new BusinessRuleViolation(field + " cannot be blank"); }
        return n;
    }

    public String tenantId() { return tenantId; }
    public UUID assetId() { return assetId; }
    public String inspectionNumber() { return inspectionNumber; }
    public InspectionType type() { return type; }
    public AssetInspectionStatus status() { return status; }
    public AssetCondition overallCondition() { return overallCondition; }
    public InspectionResult result() { return result; }
    public String inspectorId() { return inspectorId; }
    public String notes() { return notes; }
    public UUID workOrderId() { return workOrderId; }
    public Instant scheduledFor() { return scheduledFor; }
    public Instant startedAt() { return startedAt; }
    public Instant completedAt() { return completedAt; }
    public Instant cancelledAt() { return cancelledAt; }
    public List<InspectionItem> items() { return Collections.unmodifiableList(items); }
    public List<tech.kayys.syirkah.asset.domain.inspection.finding.InspectionFinding> findings() {
        return Collections.unmodifiableList(findings);
    }
}

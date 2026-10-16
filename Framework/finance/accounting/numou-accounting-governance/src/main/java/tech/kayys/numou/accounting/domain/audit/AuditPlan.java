package tech.kayys.syirkah.accounting.domain.audit;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class AuditPlan {
    private final AuditPlanId id;
    private final String tenantId;
    private final String companyId;
    private final int planYear;
    private final String title;
    private final List<AuditPlanItem> items = new ArrayList<>();
    private AuditPlanStatus status;

    public AuditPlan(AuditPlanId id, String tenantId, String companyId, int planYear, String title) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.companyId = Objects.requireNonNull(companyId, "companyId must not be null");
        this.planYear = planYear;
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.status = AuditPlanStatus.DRAFT;
    }

    public void addItem(AuditPlanItem item) {
        if (status != AuditPlanStatus.DRAFT) {
            throw new AuditViolationException("Cannot add items to a non-DRAFT plan; status=" + status);
        }
        items.add(Objects.requireNonNull(item, "item must not be null"));
    }

    public void approve(String actor) {
        if (status != AuditPlanStatus.DRAFT) {
            throw new AuditViolationException("approve requires DRAFT; current=" + status);
        }
        if (items.isEmpty()) {
            throw new AuditViolationException("Cannot approve an empty plan");
        }
        this.status = AuditPlanStatus.APPROVED;
    }

    public void activate() {
        if (status != AuditPlanStatus.APPROVED) {
            throw new AuditViolationException("activate requires APPROVED; current=" + status);
        }
        this.status = AuditPlanStatus.ACTIVE;
    }

    public void close() {
        if (status != AuditPlanStatus.ACTIVE) {
            throw new AuditViolationException("close requires ACTIVE; current=" + status);
        }
        this.status = AuditPlanStatus.CLOSED;
    }

    public AuditPlanId id() { return id; }
    public String tenantId() { return tenantId; }
    public String companyId() { return companyId; }
    public int planYear() { return planYear; }
    public String title() { return title; }
    public List<AuditPlanItem> items() { return List.copyOf(items); }
    public AuditPlanStatus status() { return status; }

    public int totalEstimatedHours() {
        return items.stream().mapToInt(AuditPlanItem::estimatedHours).sum();
    }

    public LocalDate earliestStart() {
        return items.stream().map(AuditPlanItem::plannedStartDate)
                .min(LocalDate::compareTo).orElse(LocalDate.now());
    }
}

package tech.kayys.syirkah.construction.domain.variation;

import tech.kayys.syirkah.construction.domain.variation.event.ChangeOrderApproved;
import tech.kayys.syirkah.construction.domain.variation.event.ChangeOrderCreated;
import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class ChangeOrder extends AbstractAggregateRoot<ChangeOrderId> {
    private final UUID projectId;
    private final String orderNumber;
    private String title;
    private ChangeOrderType type;
    private ChangeOrderStatus status;
    private String reason;

    private ChangeOrder(ChangeOrderId id, UUID projectId, String orderNumber, String title, ChangeOrderType type, String reason) {
        super(id);
        this.projectId = Objects.requireNonNull(projectId, "Project id cannot be null");
        this.orderNumber = Objects.requireNonNull(orderNumber, "Order number cannot be null");
        this.title = Objects.requireNonNull(title, "Title cannot be blank");
        this.type = Objects.requireNonNull(type, "Type cannot be null");
        this.reason = reason;
        this.status = ChangeOrderStatus.DRAFT;
    }

    public static ChangeOrder create(UUID projectId, String orderNumber, String title, ChangeOrderType type, String reason) {
        var co = new ChangeOrder(ChangeOrderId.generate(), projectId, orderNumber, title, type, reason);
        co.raise(new ChangeOrderCreated(UUID.randomUUID(), Instant.now(), co.id().value(), projectId, orderNumber, type));
        return co;
    }

    public void submit() {
        if (status != ChangeOrderStatus.DRAFT) throw new IllegalStateException("Only draft change orders can be submitted");
        status = ChangeOrderStatus.SUBMITTED;
    }

    public void approve() {
        if (status != ChangeOrderStatus.SUBMITTED && status != ChangeOrderStatus.UNDER_REVIEW) {
            throw new IllegalStateException("Change order cannot be approved from " + status);
        }
        status = ChangeOrderStatus.APPROVED;
        raise(new ChangeOrderApproved(UUID.randomUUID(), Instant.now(), id().value(), projectId));
    }

    public void reject(String rejectionReason) {
        if (status != ChangeOrderStatus.SUBMITTED && status != ChangeOrderStatus.UNDER_REVIEW) {
            throw new IllegalStateException("Change order cannot be rejected from " + status);
        }
        status = ChangeOrderStatus.REJECTED;
        this.reason = rejectionReason;
    }

    public UUID projectId() { return projectId; }
    public String orderNumber() { return orderNumber; }
    public String title() { return title; }
    public ChangeOrderType type() { return type; }
    public ChangeOrderStatus status() { return status; }
    public String reason() { return reason; }
}

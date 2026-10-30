package tech.kayys.numou.support.domain.ticket;

import tech.kayys.numou.ecosystem.domain.identifier.ParticipantId;
import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.exception.InvalidStateException;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.identity.domain.user.UserId;
import tech.kayys.numou.support.domain.ticket.event.TicketAssigned;
import tech.kayys.numou.support.domain.ticket.event.TicketCreated;
import tech.kayys.numou.support.domain.ticket.event.TicketStatusChanged;
import tech.kayys.numou.support.domain.ticket.event.TicketCommentAdded;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public final class Ticket extends AbstractAggregateRoot<TicketId> {

    private static final long serialVersionUID = 1L;

    private final TenantId tenantId;
    private final Requester requester;
    private final TicketType type;
    private final String subject;
    private final String description;
    private TicketPriority priority;
    private TicketStatus status;
    private WaitingReason waitingReason;
    private UserId assignedAgentId;
    private Instant assignedAt;
    private Instant resolvedAt;
    private Instant closedAt;
    private final List<TicketComment> comments = new ArrayList<>();

    private Ticket(TicketId id, TenantId tenantId, Requester requester, TicketType type,
                   TicketPriority priority, String subject, String description, Instant occurredAt) {
        super(Objects.requireNonNull(id, "id cannot be null"));
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId cannot be null");
        this.requester = Objects.requireNonNull(requester, "requester cannot be null");
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.priority = Objects.requireNonNull(priority, "priority cannot be null");
        this.subject = requireText(subject, "subject");
        this.description = requireText(description, "description");
        this.status = TicketStatus.NEW;
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        this.createdAt = occurredAt;
        this.updatedAt = occurredAt;
        raise(new TicketCreated(UUID.randomUUID(), occurredAt, id, type, priority));
    }

    public static Ticket create(TicketId id, TenantId tenantId, Requester requester,
                                TicketType type, TicketPriority priority, String subject,
                                String description, Instant occurredAt) {
        return new Ticket(id, tenantId, requester, type, priority, subject, description, occurredAt);
    }

    /**
     * Reconstitutes a previously persisted aggregate without raising creation or
     * lifecycle events. Persistence adapters must supply the complete aggregate
     * snapshot and its ordered comment history; this method validates structural
     * invariants but deliberately does not replay business commands.
     */
    public static Ticket reconstitute(
            TicketId id,
            TenantId tenantId,
            Requester requester,
            TicketType type,
            TicketPriority priority,
            TicketStatus status,
            WaitingReason waitingReason,
            String subject,
            String description,
            UserId assignedAgentId,
            Instant assignedAt,
            Instant resolvedAt,
            Instant closedAt,
            Instant createdAt,
            Instant updatedAt,
            int version,
            List<TicketComment> comments) {
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(createdAt, "createdAt cannot be null");
        Objects.requireNonNull(updatedAt, "updatedAt cannot be null");
        Objects.requireNonNull(comments, "comments cannot be null");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt cannot be before createdAt");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version cannot be negative");
        }
        if ((status == TicketStatus.WAITING) != (waitingReason != null)) {
            throw new IllegalArgumentException("waitingReason must be present exactly when status is WAITING");
        }
        if (assignedAgentId == null && assignedAt != null) {
            throw new IllegalArgumentException("assignedAt requires an assigned agent");
        }
        if (assignedAgentId != null && assignedAt == null) {
            throw new IllegalArgumentException("assigned agent requires assignedAt");
        }
        if (comments.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("comments cannot contain null entries");
        }

        Ticket ticket = new Ticket(id, tenantId, requester, type, priority, subject, description, createdAt);
        ticket.clearEvents();
        ticket.status = status;
        ticket.waitingReason = waitingReason;
        ticket.assignedAgentId = assignedAgentId;
        ticket.assignedAt = assignedAt;
        ticket.resolvedAt = resolvedAt;
        ticket.closedAt = closedAt;
        ticket.createdAt = createdAt;
        ticket.updatedAt = updatedAt;
        ticket.version = version;
        ticket.comments.addAll(comments);
        return ticket;
    }

    public TenantId tenantId() { return tenantId; }
    public Requester requester() { return requester; }
    public TicketType type() { return type; }
    public TicketPriority priority() { return priority; }
    public TicketStatus status() { return status; }
    public WaitingReason waitingReason() { return waitingReason; }
    public UserId assignedAgentId() { return assignedAgentId; }
    public Instant assignedAt() { return assignedAt; }
    public Instant resolvedAt() { return resolvedAt; }
    public Instant closedAt() { return closedAt; }
    public String subject() { return subject; }
    public String description() { return description; }

    /**
     * Returns all comments, including internal agent notes. This is an internal
     * domain view and must not be serialized directly into customer-facing responses.
     */
    public List<TicketComment> comments() {
        return Collections.unmodifiableList(comments);
    }

    /**
     * Returns only comments intended to be visible to the customer/requester.
     * Customer-facing query handlers should use this projection instead of comments().
     */
    public List<TicketComment> customerVisibleComments() {
        return comments.stream()
                .filter(comment -> !comment.internal())
                .collect(Collectors.toUnmodifiableList());
    }

    public void assign(UserId agentId, Instant occurredAt) {
        ensureNotClosed();
        Objects.requireNonNull(agentId, "agentId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        if (!agentId.equals(assignedAgentId)) {
            assignedAgentId = agentId;
            assignedAt = occurredAt;
            touch(occurredAt);
            raise(new TicketAssigned(UUID.randomUUID(), occurredAt, id(), agentId));
        }
        if (status == TicketStatus.NEW || status == TicketStatus.OPEN) {
            changeStatus(TicketStatus.IN_PROGRESS, null, occurredAt);
        }
    }

    public void changePriority(TicketPriority newPriority, Instant occurredAt) {
        ensureNotClosed();
        Objects.requireNonNull(newPriority, "priority cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        if (priority != newPriority) {
            priority = newPriority;
            touch(occurredAt);
        }
    }

    public void changeStatus(TicketStatus newStatus, WaitingReason reason, Instant occurredAt) {
        Objects.requireNonNull(newStatus, "status cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        if (status == newStatus) {
            return;
        }
        if (newStatus == TicketStatus.WAITING && reason == null) {
            throw new IllegalArgumentException("A waiting reason is required");
        }
        if (!canTransitionTo(status, newStatus)) {
            throw new InvalidStateException("Invalid ticket status transition: " + status + " -> " + newStatus);
        }
        TicketStatus previous = status;
        status = newStatus;
        waitingReason = newStatus == TicketStatus.WAITING ? reason : null;
        if (newStatus == TicketStatus.RESOLVED) {
            resolvedAt = occurredAt;
        } else if (newStatus == TicketStatus.REOPENED || newStatus == TicketStatus.IN_PROGRESS) {
            resolvedAt = null;
            closedAt = null;
        } else if (newStatus == TicketStatus.CLOSED) {
            closedAt = occurredAt;
        }
        touch(occurredAt);
        raise(new TicketStatusChanged(UUID.randomUUID(), occurredAt, id(), previous, newStatus));
    }

    public void addComment(TicketComment comment, Instant occurredAt) {
        ensureNotClosed();
        TicketComment validatedComment = Objects.requireNonNull(comment, "comment cannot be null");
        Instant eventTime = Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        comments.add(validatedComment);
        touch(eventTime);
        raise(new TicketCommentAdded(UUID.randomUUID(), eventTime, id(), validatedComment));
    }

    private static boolean canTransitionTo(TicketStatus current, TicketStatus target) {
        return switch (current) {
            case NEW -> target == TicketStatus.OPEN || target == TicketStatus.IN_PROGRESS
                    || target == TicketStatus.CLOSED;
            case OPEN -> target == TicketStatus.IN_PROGRESS || target == TicketStatus.WAITING
                    || target == TicketStatus.RESOLVED || target == TicketStatus.CLOSED;
            case IN_PROGRESS -> target == TicketStatus.WAITING || target == TicketStatus.RESOLVED
                    || target == TicketStatus.CLOSED;
            case WAITING -> target == TicketStatus.OPEN || target == TicketStatus.IN_PROGRESS
                    || target == TicketStatus.RESOLVED || target == TicketStatus.CLOSED;
            case RESOLVED -> target == TicketStatus.REOPENED || target == TicketStatus.CLOSED;
            case REOPENED -> target == TicketStatus.OPEN || target == TicketStatus.IN_PROGRESS
                    || target == TicketStatus.WAITING || target == TicketStatus.RESOLVED;
            case CLOSED -> false;
        };
    }

    private void ensureNotClosed() {
        if (status == TicketStatus.CLOSED) {
            throw new InvalidStateException("Closed ticket cannot be changed");
        }
    }

    private void touch(Instant occurredAt) {
        setUpdatedAt(occurredAt);
        incrementVersion();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value.trim();
    }
}

package tech.kayys.numou.feedback.domain;

import tech.kayys.numou.feedback.domain.event.FeedbackStatusChanged;
import tech.kayys.numou.feedback.domain.event.FeedbackSubmitted;
import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.exception.InvalidStateException;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Tenant-owned feedback submission and triage lifecycle.
 * Comments, votes, ratings, attachments and diagnostic evidence are separate
 * resources introduced by later milestones.
 */
public final class Feedback extends AbstractAggregateRoot<FeedbackId> {
    private static final long serialVersionUID = 1L;

    private final TenantId tenantId;
    private final FeedbackActor author;
    private final FeedbackKind kind;
    private final String title;
    private final String description;
    private final FeedbackContext context;
    private FeedbackPriority priority;
    private FeedbackStatus status;

    private Feedback(FeedbackId id, TenantId tenantId, FeedbackActor author,
                     FeedbackKind kind, FeedbackPriority priority, String title,
                     String description, FeedbackContext context, Instant occurredAt) {
        super(Objects.requireNonNull(id, "id cannot be null"));
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId cannot be null");
        this.author = Objects.requireNonNull(author, "author cannot be null");
        this.kind = Objects.requireNonNull(kind, "kind cannot be null");
        this.priority = Objects.requireNonNull(priority, "priority cannot be null");
        this.title = requireText(title, "title", 200);
        this.description = requireText(description, "description", 20_000);
        this.context = context == null ? FeedbackContext.empty() : context;
        Instant createdAt = Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        this.status = FeedbackStatus.SUBMITTED;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        raise(new FeedbackSubmitted(UUID.randomUUID(), createdAt, id, kind.code()));
    }

    public static Feedback submit(FeedbackId id, TenantId tenantId, FeedbackActor author,
                                  FeedbackKind kind, FeedbackPriority priority, String title,
                                  String description, FeedbackContext context, Instant occurredAt) {
        return new Feedback(id, tenantId, author, kind, priority, title,
                description, context, occurredAt);
    }

    /** Reconstitutes persisted state without raising a new submission event. */
    public static Feedback rehydrate(FeedbackId id, TenantId tenantId, FeedbackActor author,
                                     FeedbackKind kind, FeedbackPriority priority, String title,
                                     String description, FeedbackContext context, FeedbackStatus status,
                                     Instant createdAt, Instant updatedAt, int version) {
        Feedback feedback = new Feedback(id, tenantId, author, kind, priority, title,
                description, context, createdAt);
        feedback.status = Objects.requireNonNull(status, "status cannot be null");
        feedback.setCreatedAt(Objects.requireNonNull(createdAt, "createdAt cannot be null"));
        feedback.setUpdatedAt(Objects.requireNonNull(updatedAt, "updatedAt cannot be null"));
        if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("updatedAt cannot be before createdAt");
        if (version < 0) throw new IllegalArgumentException("version cannot be negative");
        feedback.setVersion(version);
        feedback.clearEvents();
        return feedback;
    }

    public TenantId tenantId() { return tenantId; }
    public FeedbackActor author() { return author; }
    public FeedbackKind kind() { return kind; }
    public FeedbackPriority priority() { return priority; }
    public FeedbackStatus status() { return status; }
    public String title() { return title; }
    public String description() { return description; }
    public FeedbackContext context() { return context; }
    public Instant createdAt() { return getCreatedAt(); }
    public Instant updatedAt() { return getUpdatedAt(); }
    public int version() { return getVersion(); }

    public void changePriority(FeedbackPriority newPriority, Instant occurredAt) {
        ensureNotTerminal();
        Objects.requireNonNull(newPriority, "priority cannot be null");
        Instant eventTime = Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        if (priority != newPriority) {
            priority = newPriority;
            touch(eventTime);
        }
    }

    public void triage(Instant at) { transitionTo(FeedbackStatus.TRIAGED, at); }
    public void accept(Instant at) { transitionTo(FeedbackStatus.ACCEPTED, at); }
    public void reject(Instant at) { transitionTo(FeedbackStatus.REJECTED, at); }
    public void markDuplicate(Instant at) { transitionTo(FeedbackStatus.DUPLICATE, at); }
    public void startWork(Instant at) { transitionTo(FeedbackStatus.IN_PROGRESS, at); }
    public void beginReview(Instant at) { transitionTo(FeedbackStatus.UNDER_REVIEW, at); }
    public void resolve(Instant at) { transitionTo(FeedbackStatus.RESOLVED, at); }
    public void close(Instant at) { transitionTo(FeedbackStatus.CLOSED, at); }
    public void reopen(Instant at) { transitionTo(FeedbackStatus.REOPENED, at); }

    private void transitionTo(FeedbackStatus target, Instant occurredAt) {
        Objects.requireNonNull(target, "target cannot be null");
        Instant eventTime = Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        if (status == target) return;
        if (!canTransition(status, target)) {
            throw new InvalidStateException("Invalid feedback status transition: " + status + " -> " + target);
        }
        FeedbackStatus previous = status;
        status = target;
        touch(eventTime);
        raise(new FeedbackStatusChanged(UUID.randomUUID(), eventTime, id(), previous, target));
    }

    private static boolean canTransition(FeedbackStatus current, FeedbackStatus target) {
        return switch (current) {
            case SUBMITTED -> target == FeedbackStatus.TRIAGED;
            case TRIAGED -> target == FeedbackStatus.ACCEPTED
                    || target == FeedbackStatus.REJECTED || target == FeedbackStatus.DUPLICATE;
            case ACCEPTED -> target == FeedbackStatus.IN_PROGRESS || target == FeedbackStatus.CLOSED;
            case IN_PROGRESS -> target == FeedbackStatus.UNDER_REVIEW
                    || target == FeedbackStatus.RESOLVED || target == FeedbackStatus.CLOSED;
            case UNDER_REVIEW -> target == FeedbackStatus.IN_PROGRESS
                    || target == FeedbackStatus.RESOLVED || target == FeedbackStatus.CLOSED;
            case RESOLVED -> target == FeedbackStatus.REOPENED || target == FeedbackStatus.CLOSED;
            case REOPENED -> target == FeedbackStatus.IN_PROGRESS || target == FeedbackStatus.CLOSED;
            case REJECTED, DUPLICATE -> target == FeedbackStatus.CLOSED;
            case CLOSED -> false;
        };
    }

    private void ensureNotTerminal() {
        if (status == FeedbackStatus.CLOSED || status == FeedbackStatus.REJECTED
                || status == FeedbackStatus.DUPLICATE) {
            throw new InvalidStateException("Terminal feedback cannot be changed");
        }
    }

    private void touch(Instant at) {
        setUpdatedAt(at);
        incrementVersion();
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " exceeds " + maxLength + " characters");
        }
        return normalized;
    }
}
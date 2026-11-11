package tech.kayys.numou.feedback.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Tenant-owned feedback aggregate. It intentionally references users and source records
 * by opaque IDs so Feedback does not own or depend on Customer, Support, or product domains.
 */
public final class FeedbackItem {
    private final FeedbackId id;
    private final UUID tenantId;
    private final UUID submitterId; // null only when the host's anonymous-submission policy allows it
    private final FeedbackType type;
    private final String title;
    private final String description;
    private final String sourceApplication;
    private final String sourceModule;
    private final String sourceReference;
    private FeedbackPriority priority;
    private FeedbackStatus status;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;
    private final List<FeedbackEvent> pendingEvents = new ArrayList<>();

    private FeedbackItem(FeedbackId id, UUID tenantId, UUID submitterId, FeedbackType type,
                         String title, String description, String sourceApplication,
                         String sourceModule, String sourceReference, FeedbackPriority priority,
                         Instant now) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId cannot be null");
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.title = requireText(title, "title", 200);
        this.description = requireText(description, "description", 20_000);
        this.sourceApplication = optionalText(sourceApplication, "sourceApplication", 100);
        this.sourceModule = optionalText(sourceModule, "sourceModule", 100);
        this.sourceReference = optionalText(sourceReference, "sourceReference", 200);
        this.priority = Objects.requireNonNull(priority, "priority cannot be null");
        this.submitterId = submitterId;
        this.status = FeedbackStatus.SUBMITTED;
        this.createdAt = Objects.requireNonNull(now, "now cannot be null");
        this.updatedAt = now;
        pendingEvents.add(new FeedbackSubmitted(UUID.randomUUID(), id, tenantId, type, now));
    }

    /** Reconstitutes persisted state without emitting a new domain event. */
    public static FeedbackItem restore(FeedbackId id, UUID tenantId, UUID submitterId,
                                       FeedbackType type, String title, String description,
                                       String sourceApplication, String sourceModule,
                                       String sourceReference, FeedbackPriority priority,
                                       FeedbackStatus status, Instant createdAt,
                                       Instant updatedAt, long version) {
        FeedbackItem item = new FeedbackItem(id, tenantId, submitterId, type, title, description,
                sourceApplication, sourceModule, sourceReference, priority, createdAt);
        item.status = Objects.requireNonNull(status, "status cannot be null");
        item.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt cannot be null");
        if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("updatedAt cannot be before createdAt");
        if (version < 0) throw new IllegalArgumentException("version cannot be negative");
        item.version = version;
        item.pendingEvents.clear();
        return item;
    }

    public static FeedbackItem submit(FeedbackId id, UUID tenantId, UUID submitterId,
                                      FeedbackType type, String title, String description,
                                      String sourceApplication, String sourceModule,
                                      String sourceReference, FeedbackPriority priority, Instant now) {
        return new FeedbackItem(id, tenantId, submitterId, type, title, description,
                sourceApplication, sourceModule, sourceReference, priority, now);
    }

    public void changeStatus(FeedbackStatus target, Instant now) {
        Objects.requireNonNull(target, "target cannot be null");
        Objects.requireNonNull(now, "now cannot be null");
        if (status == target) return;
        if (!canTransition(status, target)) {
            throw new IllegalStateException("Invalid feedback status transition: " + status + " -> " + target);
        }
        FeedbackStatus previous = status;
        status = target;
        touch(now);
        pendingEvents.add(new FeedbackStatusChanged(UUID.randomUUID(), id, tenantId, previous, target, now));
    }

    public void changePriority(FeedbackPriority target, Instant now) {
        Objects.requireNonNull(target, "target cannot be null");
        Objects.requireNonNull(now, "now cannot be null");
        if (status == FeedbackStatus.CLOSED) throw new IllegalStateException("Closed feedback cannot be changed");
        if (priority != target) {
            priority = target;
            touch(now);
        }
    }

    private static boolean canTransition(FeedbackStatus from, FeedbackStatus to) {
        return switch (from) {
            case SUBMITTED -> to == FeedbackStatus.TRIAGED || to == FeedbackStatus.REJECTED
                    || to == FeedbackStatus.DUPLICATE;
            case TRIAGED -> to == FeedbackStatus.ACCEPTED || to == FeedbackStatus.REJECTED
                    || to == FeedbackStatus.DUPLICATE;
            case ACCEPTED -> to == FeedbackStatus.IN_PROGRESS || to == FeedbackStatus.REJECTED;
            case IN_PROGRESS -> to == FeedbackStatus.UNDER_REVIEW || to == FeedbackStatus.RESOLVED;
            case UNDER_REVIEW -> to == FeedbackStatus.IN_PROGRESS || to == FeedbackStatus.RESOLVED;
            case RESOLVED -> to == FeedbackStatus.REOPENED || to == FeedbackStatus.CLOSED;
            case REOPENED -> to == FeedbackStatus.IN_PROGRESS || to == FeedbackStatus.REJECTED;
            case REJECTED, DUPLICATE -> to == FeedbackStatus.CLOSED;
            case CLOSED -> false;
        };
    }

    private void touch(Instant now) {
        if (now.isBefore(updatedAt)) throw new IllegalArgumentException("now cannot be before updatedAt");
        updatedAt = now;
        version++;
    }

    private static String requireText(String value, String field, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException(field + " exceeds " + max + " characters");
        return normalized;
    }

    private static String optionalText(String value, String field, int max) {
        if (value == null || value.isBlank()) return null;
        return requireText(value, field, max);
    }

    public FeedbackId id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID submitterId() { return submitterId; }
    public FeedbackType type() { return type; }
    public String title() { return title; }
    public String description() { return description; }
    public String sourceApplication() { return sourceApplication; }
    public String sourceModule() { return sourceModule; }
    public String sourceReference() { return sourceReference; }
    public FeedbackPriority priority() { return priority; }
    public FeedbackStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public long version() { return version; }

    /** Drain only after the caller has successfully persisted the aggregate and its outbox rows. */
    public List<FeedbackEvent> pendingEvents() { return List.copyOf(pendingEvents); }
    public void clearPendingEvents() { pendingEvents.clear(); }
}
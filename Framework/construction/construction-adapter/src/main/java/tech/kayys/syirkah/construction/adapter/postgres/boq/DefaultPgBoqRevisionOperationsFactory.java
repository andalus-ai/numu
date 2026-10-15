package tech.kayys.syirkah.construction.adapter.postgres.boq;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.SqlConnection;
import tech.kayys.syirkah.construction.domain.boq.BoqCalculationPolicy;
import tech.kayys.syirkah.construction.domain.boq.BoqRevisionSnapshot;
import tech.kayys.syirkah.construction.spi.idempotency.TransactionalCommandContext;
import tech.kayys.syirkah.construction.spi.boq.BoqRevisionAuthorization;
import tech.kayys.syirkah.construction.spi.outbox.TransactionalOutboxEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Orchestrates BOQ revision transitions using a single caller-owned transaction.
 * The live aggregate table mapping is supplied separately because the source dump
 * does not establish its physical table/column names.
 */
public final class DefaultPgBoqRevisionOperationsFactory implements PgBoqRevisionOperationsFactory {
    private final PgLiveBoqRevisionStoreFactory liveStoreFactory;
    private final BoqRevisionAuthorization authorization;

    public DefaultPgBoqRevisionOperationsFactory(PgLiveBoqRevisionStoreFactory liveStoreFactory,
                                                 BoqRevisionAuthorization authorization) {
        this.liveStoreFactory = Objects.requireNonNull(liveStoreFactory, "liveStoreFactory");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
    }

    @Override
    public TransactionalCommandContext.BoqRevisionOperations create(SqlConnection connection) {
        Objects.requireNonNull(connection, "connection");
        PgLiveBoqRevisionStore live = liveStoreFactory.create(connection);
        PgBoqRevisionSnapshotRepository snapshots = new PgBoqRevisionSnapshotRepository(connection);
        PgBoqRevisionNumberAllocator allocator = new PgBoqRevisionNumberAllocator(connection);
        return new Operations(connection, live, snapshots, allocator, authorization);
    }

    private static final class Operations implements TransactionalCommandContext.BoqRevisionOperations {
        private final SqlConnection connection;
        private final PgLiveBoqRevisionStore live;
        private final PgBoqRevisionSnapshotRepository snapshots;
        private final PgBoqRevisionNumberAllocator allocator;
        private final BoqRevisionAuthorization authorization;

        private Operations(SqlConnection connection, PgLiveBoqRevisionStore live,
                           PgBoqRevisionSnapshotRepository snapshots, PgBoqRevisionNumberAllocator allocator,
                           BoqRevisionAuthorization authorization) {
            this.connection = connection;
            this.live = live;
            this.snapshots = snapshots;
            this.allocator = allocator;
            this.authorization = authorization;
        }

        @Override
        public Uni<BoqRevisionSnapshot> captureDraft(String tenantId, UUID boqId, UUID actorId,
                                                      BoqCalculationPolicy policy, Instant now) {
            requireText(tenantId, "tenantId"); require(boqId, "boqId"); require(actorId, "actorId"); require(policy, "policy"); require(now, "now");
            return authorize(tenantId, actorId, boqId, BoqRevisionAuthorization.Action.CAPTURE_DRAFT)
                    .flatMap(ignored -> live.loadForUpdate(tenantId, boqId)).flatMap(boq -> {
                requireStatus(boq, "DRAFT");
                return Uni.createFrom().completionStage(snapshots.findAllByBoqId(tenantId, boqId)).flatMap(existing -> {
                    int minimum = existing.isEmpty() ? boq.currentRevision() : Math.max(
                            boq.currentRevision() + 1,
                            existing.stream().mapToInt(BoqRevisionSnapshot::revisionNumber).max().orElse(0) + 1);
                    return allocator.allocateNext(tenantId, boqId, minimum).flatMap(revision -> live.loadItems(tenantId, boqId)
                            .flatMap(items -> {
                                List<BoqRevisionSnapshot.ItemSnapshot> copied = items.stream().map(item -> {
                                    BigDecimal amount = policy.calculateLineAmount(item.quantity(), item.quantityUnit(), item.rate(), item.rateUnit());
                                    return new BoqRevisionSnapshot.ItemSnapshot(item.itemId(), item.wbsNodeId(),
                                            item.itemCode(), item.description(), item.itemType(), item.quantity(),
                                            item.quantityUnit(), item.rate(), item.rateUnit(), item.currencyCode(), amount);
                                }).toList();
                                BoqRevisionSnapshot snapshot = BoqRevisionSnapshot.draft(boq.id(), boq.projectId(),
                                        revision, boq.name(), copied, policy.version(), actorId, now);
                                return Uni.createFrom().completionStage(snapshots.insert(tenantId, snapshot))
                                        .flatMap(v -> live.compareAndSetState(tenantId, boq.id(), boq.currentRevision(), "DRAFT",
                                                revision, "DRAFT", now))
                                        .flatMap(updated -> updated ? appendEvent(boq, snapshot, "BoqRevisionCaptured", now)
                                                .replaceWith(snapshot) : conflict("BOQ changed during revision capture"));
                            }));
                });
            });
        }

        @Override
        public Uni<BoqRevisionSnapshot> submitForReview(String tenantId, UUID boqId, int revisionNumber, UUID actorId, Instant now) {
            requireText(tenantId, "tenantId"); require(boqId, "boqId"); require(actorId, "actorId"); require(now, "now");
            return authorize(tenantId, actorId, boqId, BoqRevisionAuthorization.Action.SUBMIT_FOR_REVIEW)
                    .flatMap(ignored -> live.loadForUpdate(tenantId, boqId)).flatMap(boq -> {
                requireStatus(boq, "DRAFT");
                if (boq.currentRevision() != revisionNumber) return conflict("Only the current BOQ revision can be submitted");
                return Uni.createFrom().completionStage(snapshots.find(tenantId, boqId, revisionNumber)).flatMap(found -> {
                    if (found.isEmpty()) return notFound("BOQ revision not found");
                    BoqRevisionSnapshot before = found.get();
                    BoqRevisionSnapshot after = before.submit(actorId, now);
                    return Uni.createFrom().completionStage(snapshots.replaceIfStatus(tenantId, after, BoqRevisionSnapshot.RevisionStatus.DRAFT))
                            .flatMap(v -> live.compareAndSetState(tenantId, boqId, boq.currentRevision(), "DRAFT",
                                    boq.currentRevision(), "UNDER_REVIEW", now))
                            .flatMap(updated -> updated ? appendEvent(boq, after, "BoqRevisionSubmittedForReview", now)
                                    .replaceWith(after) : conflict("BOQ changed during submission"));
                });
            });
        }

        @Override
        public Uni<BoqRevisionSnapshot> approve(String tenantId, UUID boqId, int revisionNumber, UUID actorId, Instant now) {
            requireText(tenantId, "tenantId"); require(boqId, "boqId"); require(actorId, "actorId"); require(now, "now");
            return authorize(tenantId, actorId, boqId, BoqRevisionAuthorization.Action.APPROVE)
                    .flatMap(ignored -> live.loadForUpdate(tenantId, boqId)).flatMap(boq -> {
                requireStatus(boq, "UNDER_REVIEW");
                if (boq.currentRevision() != revisionNumber) return conflict("Only the current BOQ revision can be approved");
                return Uni.createFrom().completionStage(snapshots.find(tenantId, boqId, revisionNumber)).flatMap(found -> {
                    if (found.isEmpty()) return notFound("BOQ revision not found");
                    BoqRevisionSnapshot before = found.get();
                    if (actorId.equals(before.submittedBy())) {
                        return conflict("The submitting actor cannot approve the same BOQ revision");
                    }
                    BoqRevisionSnapshot after = before.approve(actorId, now);
                    return Uni.createFrom().completionStage(snapshots.replaceIfStatus(tenantId, after, BoqRevisionSnapshot.RevisionStatus.UNDER_REVIEW))
                            .flatMap(v -> live.compareAndSetState(tenantId, boqId, boq.currentRevision(), "UNDER_REVIEW",
                                    boq.currentRevision(), "APPROVED", now))
                            .flatMap(updated -> updated ? appendEvent(boq, after, "BoqRevisionApproved", now)
                                    .replaceWith(after) : conflict("BOQ changed during approval"));
                });
            });
        }

        private Uni<Void> authorize(String tenantId, UUID actorId, UUID boqId, BoqRevisionAuthorization.Action action) {
            return authorization.authorize(tenantId, actorId, boqId, action);
        }

        private Uni<Void> appendEvent(PgLiveBoqRevisionStore.LiveBoq boq, BoqRevisionSnapshot snapshot,
                                      String eventType, Instant now) {
            String payload = "{\"boqId\":\"" + boq.id() + "\",\"projectId\":\"" + boq.projectId()
                    + "\",\"revisionNumber\":" + snapshot.revisionNumber() + ",\"status\":\""
                    + snapshot.status().name() + "\"}";
            TransactionalOutboxEvent event = new TransactionalOutboxEvent(UUID.randomUUID(), boq.tenantId(),
                    "BOQ", boq.id(), eventType, payload, now);
            return connection.preparedQuery("INSERT INTO construction_outbox_event "
                    + "(event_id, tenant_id, aggregate_type, aggregate_id, event_type, payload, occurred_at) "
                    + "VALUES ($1, $2, $3, $4, $5, $6::jsonb, $7)")
                    .execute(io.vertx.mutiny.sqlclient.Tuple.of(event.eventId(), event.tenantId(), event.aggregateType(),
                            event.aggregateId(), event.eventType(), event.payloadJson(),
                            event.occurredAt().atOffset(java.time.ZoneOffset.UTC))).replaceWithVoid();
        }

        private static void requireStatus(PgLiveBoqRevisionStore.LiveBoq boq, String expected) {
            if (!expected.equals(boq.status())) throw new IllegalStateException(
                    "Expected BOQ status " + expected + " but was " + boq.status());
        }
        private static void requireText(String value, String name) { if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required"); }
        private static <T> T require(T value, String name) { return Objects.requireNonNull(value, name); }
        private static <T> Uni<T> conflict(String message) { return Uni.createFrom().failure(new RevisionWorkflowConflictException(message)); }
        private static <T> Uni<T> notFound(String message) { return Uni.createFrom().failure(new RevisionWorkflowNotFoundException(message)); }
    }

    public static final class RevisionWorkflowConflictException extends RuntimeException {
        public RevisionWorkflowConflictException(String message) { super(message); }
    }
    public static final class RevisionWorkflowNotFoundException extends RuntimeException {
        public RevisionWorkflowNotFoundException(String message) { super(message); }
    }
}

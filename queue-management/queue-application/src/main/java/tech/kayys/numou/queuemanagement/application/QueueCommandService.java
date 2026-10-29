package tech.kayys.numou.queuemanagement.application;

import tech.kayys.numou.queuemanagement.domain.QueueDefinition;
import tech.kayys.numou.queuemanagement.domain.QueueEntry;
import tech.kayys.numou.queuemanagement.domain.QueueEntryId;
import tech.kayys.numou.queuemanagement.domain.QueueEntryStatus;
import tech.kayys.numou.queuemanagement.domain.QueueId;
import tech.kayys.numou.queuemanagement.domain.QueueProfile;
import tech.kayys.numou.queuemanagement.domain.QueueSourceReference;
import tech.kayys.numou.queuemanagement.spi.ClaimMode;
import tech.kayys.numou.queuemanagement.spi.QueueAuditRecord;
import tech.kayys.numou.queuemanagement.spi.QueueCommandContext;
import tech.kayys.numou.queuemanagement.spi.QueueCommandResult;
import tech.kayys.numou.queuemanagement.spi.QueueCommandTransactionPort;
import tech.kayys.numou.queuemanagement.spi.QueueEventRecord;
import tech.kayys.numou.queuemanagement.spi.QueueRepositoryPort;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Framework-neutral command orchestration. Every write runs through a transaction boundary
 * that is responsible for atomic state/audit/outbox/idempotency persistence.
 */
public final class QueueCommandService {
    private static final int EVENT_SCHEMA_VERSION = 1;

    private final QueueRepositoryPort queues;
    private final QueueCommandTransactionPort transactions;

    public QueueCommandService(QueueRepositoryPort queues, QueueCommandTransactionPort transactions) {
        this.queues = Objects.requireNonNull(queues, "queues must not be null");
        this.transactions = Objects.requireNonNull(transactions, "transactions must not be null");
    }

    public QueueEntry join(QueueCommandContext context, QueueId queueId,
                           QueueSourceReference source, int priority) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(queueId, "queueId must not be null");
        Objects.requireNonNull(source, "source must not be null");
        return transactions.execute(context, "queue.entry.join",
                QueueRequestFingerprint.sha256("queue.entry.join", queueId.value().toString(),
                        source.capability(), source.resourceType(), source.resourceId(), Integer.toString(priority)), () -> {
            QueueDefinition queue = requireQueue(context, queueId);
            QueueEntry entry = QueueEntry.join(QueueEntryId.generate(), queue, source, priority, context.occurredAt());
            queues.saveEntry(entry);
            return result(context, queueId, entry, "QueueEntryJoined", "ENTRY_JOINED");
        });
    }

    /**
     * Claims exactly one eligible entry. The repository contract requires the selection and
     * transition to be atomic under concurrency; application-level read-then-save is forbidden.
     */
    public QueueEntry claimNext(QueueCommandContext context, QueueId queueId, ClaimMode mode) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(queueId, "queueId must not be null");
        Objects.requireNonNull(mode, "mode must not be null");
        String operationScope = "queue.entry.claim-next." + mode.name();
        return transactions.execute(context, operationScope,
                QueueRequestFingerprint.sha256(operationScope, queueId.value().toString(), mode.name()), () -> {
            QueueDefinition queue = requireQueue(context, queueId);
            if (mode == ClaimMode.CALL_SERVICE_ENTRY && queue.profile() != QueueProfile.WAITING_SERVICE) {
                throw new IllegalStateException("CALL_SERVICE_ENTRY requires a waiting/service queue");
            }
            if (mode == ClaimMode.OFFER_DISPATCH_ENTRY && queue.profile() != QueueProfile.DISPATCH) {
                throw new IllegalStateException("OFFER_DISPATCH_ENTRY requires a dispatch queue");
            }
            QueueEntry entry = Objects.requireNonNull(
                    queues.claimNext(context.tenantId(), queueId, mode, context.actorId(), context.occurredAt()),
                    "claimNext must return the persisted claimed entry");
            return result(context, queueId, entry, "QueueEntryClaimed", "ENTRY_CLAIMED_" + mode.name());
        });
    }

    public QueueEntry transition(QueueCommandContext context, QueueId queueId,
                                 QueueEntryId entryId, QueueEntryAction action) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(queueId, "queueId must not be null");
        Objects.requireNonNull(entryId, "entryId must not be null");
        Objects.requireNonNull(action, "action must not be null");
        String operationScope = "queue.entry.transition." + action.name();
        return transactions.execute(context, operationScope,
                QueueRequestFingerprint.sha256(operationScope, queueId.value().toString(),
                        entryId.value().toString(), action.name()), () -> {
            QueueEntry entry = queues.findEntry(context.tenantId(), queueId, entryId)
                    .orElseThrow(() -> new IllegalArgumentException("Queue entry not found"));
            if (!context.tenantId().equals(entry.tenantId()) || !queueId.equals(entry.queueId())) {
                throw new IllegalArgumentException("Queue entry is outside the requested tenant/queue scope");
            }
            switch (action) {
                case START_SERVING -> entry.startServing(context.occurredAt());
                case COMPLETE_SERVICE -> entry.complete(context.occurredAt());
                case ACCEPT_DISPATCH_OFFER -> entry.acceptOffer(context.occurredAt());
                case REJECT_DISPATCH_OFFER -> entry.rejectOffer(context.occurredAt());
                case COMPLETE_DISPATCH -> entry.completeDispatch(context.occurredAt());
                case RESOLVE_FROM_SOURCE -> entry.resolveFromSource(context.occurredAt());
                case MARK_NO_SHOW -> entry.markNoShow(context.occurredAt());
                case ABANDON -> entry.abandon(context.occurredAt());
                case TRANSFER -> entry.transfer(context.occurredAt());
                case REQUEUE -> entry.requeue(context.occurredAt());
            }
            queues.saveEntry(entry);
            return result(context, queueId, entry, "QueueEntryStatusChanged", "ENTRY_" + action.name());
        });
    }

    private QueueDefinition requireQueue(QueueCommandContext context, QueueId queueId) {
        QueueDefinition queue = queues.findQueue(context.tenantId(), queueId)
                .orElseThrow(() -> new IllegalArgumentException("Queue not found"));
        if (!context.tenantId().equals(queue.tenantId()) || !queueId.equals(queue.id())) {
            throw new IllegalArgumentException("Queue is outside the requested tenant scope");
        }
        return queue;
    }

    private QueueCommandResult<QueueEntry> result(QueueCommandContext context, QueueId queueId,
                                                   QueueEntry entry, String eventType, String action) {
        String entryId = entry.id().value().toString();
        String queueIdValue = queueId.value().toString();
        QueueAuditRecord audit = new QueueAuditRecord(context.tenantId(), context.actorId(), action,
                "queue-entry", entryId, context.correlationId(), context.occurredAt());
        String payload = "{\"queueId\":\"" + queueIdValue + "\",\"entryId\":\"" + entryId
                + "\",\"status\":\"" + entry.status().name() + "\"}";
        QueueEventRecord event = new QueueEventRecord(context.tenantId(), UUID.randomUUID().toString(),
                eventType, EVENT_SCHEMA_VERSION, "queue-entry", entryId, context.correlationId(),
                context.occurredAt(), payload);
        return new QueueCommandResult<>(entry, List.of(audit), List.of(event));
    }
}
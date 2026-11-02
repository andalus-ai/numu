package tech.kayys.numou.automotiveparts.purchasing;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.identity.application.port.EffectivePermissionPort;
import tech.kayys.numou.identity.domain.user.UserId;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Reactive operator facade for hosts using Numou Identity and JDBC persistence.
 *
 * <p>Effective permissions are resolved asynchronously through Identity. JDBC
 * reads and state changes are always scheduled on Mutiny's worker pool; this
 * class never blocks the request/event-loop thread. Trusted operator context
 * is captured synchronously before starting asynchronous work so request
 * scoped state is not read from a worker thread.
 */
public final class IdentityReceiptPostingOperations {
    public static final String INSPECT_PERMISSION = "automotive-parts.receipt-posting.inspect";
    public static final String REQUEUE_PERMISSION = "automotive-parts.receipt-posting.requeue";

    private final CurrentReceiptPostingOperatorPort currentOperator;
    private final EffectivePermissionPort effectivePermissions;
    private final JdbcReceiptPostingOperationsRepository postings;
    private final JdbcReceiptPostingRequeueAuditRepository audit;
    private final ReceiptPostingLedger ledger;

    public IdentityReceiptPostingOperations(CurrentReceiptPostingOperatorPort currentOperator,
                                            EffectivePermissionPort effectivePermissions,
                                            JdbcReceiptPostingOperationsRepository postings,
                                            JdbcReceiptPostingRequeueAuditRepository audit,
                                            ReceiptPostingLedger ledger) {
        this.currentOperator = Objects.requireNonNull(currentOperator, "currentOperator is required");
        this.effectivePermissions = Objects.requireNonNull(effectivePermissions, "effectivePermissions is required");
        this.postings = Objects.requireNonNull(postings, "postings is required");
        this.audit = Objects.requireNonNull(audit, "audit is required");
        this.ledger = Objects.requireNonNull(ledger, "ledger is required");
    }

    public Uni<ReceiptPostingOperationsSummary> summary() {
        ReceiptPostingOperatorContext operator = current();
        return authorize(operator, INSPECT_PERMISSION)
                .flatMap(ignored -> blocking(() -> postings.summary(operator.tenantId())));
    }

    public Uni<DeadLetterReceiptPostingPage> listDeadLetters(String cursor, int limit) {
        ReceiptPostingOperatorContext operator = current();
        if (limit < 1 || limit > 200) {
            throw new IllegalArgumentException("limit must be between 1 and 200");
        }
        return authorize(operator, INSPECT_PERMISSION)
                .flatMap(ignored -> blocking(() -> postings.findDeadLetters(operator.tenantId(), cursor, limit)));
    }

    public Uni<Optional<DeadLetterReceiptPostingDetail>> getDeadLetter(String receiptId, int historyLimit) {
        ReceiptPostingOperatorContext operator = current();
        require(receiptId, "receiptId");
        if (historyLimit < 1 || historyLimit > 200) {
            throw new IllegalArgumentException("historyLimit must be between 1 and 200");
        }
        return authorize(operator, INSPECT_PERMISSION).flatMap(ignored -> blocking(() -> {
            Optional<DeadLetterReceiptPosting> posting =
                    postings.findDeadLetter(operator.tenantId(), receiptId);
            return posting.map(value -> new DeadLetterReceiptPostingDetail(value,
                    audit.findByReceipt(operator.tenantId(), receiptId, historyLimit)));
        }));
    }

    public Uni<Void> requeue(String receiptId, String reason) {
        ReceiptPostingOperatorContext operator = current();
        require(receiptId, "receiptId");
        require(reason, "reason");
        String normalizedReason = reason.trim();
        if (normalizedReason.length() > 2000) {
            throw new IllegalArgumentException("reason must not exceed 2000 characters");
        }

        return authorize(operator, REQUEUE_PERMISSION).flatMap(ignored -> blocking(() -> {
            String fingerprint = postings.findFingerprint(operator.tenantId(), receiptId)
                    .orElseThrow(() -> new IllegalArgumentException("Dead-letter receipt posting not found"));
            // The ledger transition and V1003 database trigger write the audit event atomically.
            ledger.requeue(operator.tenantId(), receiptId, fingerprint, operator.actorId(), normalizedReason);
            return Boolean.TRUE;
        })).replaceWithVoid();
    }

    private Uni<Void> authorize(ReceiptPostingOperatorContext operator, String permission) {
        final TenantId tenantId;
        final UserId userId;
        try {
            tenantId = TenantId.of(operator.tenantId());
            userId = UserId.of(UUID.fromString(operator.actorId()));
        } catch (RuntimeException invalidIdentity) {
            return Uni.createFrom().failure(
                    new SecurityException("Authenticated tenant and actor identifiers are invalid", invalidIdentity));
        }

        return Uni.createFrom().deferred(() -> effectivePermissions.permissionsFor(tenantId, userId))
                // A permission service outage must fail closed, never bypass authorization.
                .onFailure().recoverWithItem(Set.<String>of())
                .map(granted -> {
                    if (granted == null || !granted.contains(permission)) {
                        throw new SecurityException("Operator is not authorized for " + permission);
                    }
                    return Boolean.TRUE;
                })
                .replaceWithVoid();
    }

    private ReceiptPostingOperatorContext current() {
        ReceiptPostingOperatorContext context = currentOperator.current();
        if (context == null) {
            throw new SecurityException("Authenticated receipt posting operator is required");
        }
        return context;
    }

    private static <T> Uni<T> blocking(Supplier<T> work) {
        return Uni.createFrom().item(work).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}

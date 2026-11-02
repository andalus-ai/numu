package tech.kayys.numou.automotiveparts.purchasing;

import io.smallrye.mutiny.Uni;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Reactive operator facade for receipt-posting inspection and recovery.
 *
 * <p>Identity authorization remains asynchronous. JDBC work is explicitly dispatched to the
 * supplied blocking executor; no JDBC call is made on the caller's event-loop thread.
 * The authenticated operator context is captured before asynchronous work starts and is
 * never accepted as a method argument.
 */
public final class ReactiveReceiptPostingOperations {
    private final CurrentReceiptPostingOperatorPort currentOperator;
    private final ReceiptPostingOperationsAuthorizationPort authorization;
    private final JdbcReceiptPostingOperationsRepository postings;
    private final JdbcReceiptPostingRequeueAuditRepository audit;
    private final ReceiptPostingLedger ledger;
    private final Executor blockingExecutor;

    public ReactiveReceiptPostingOperations(
            CurrentReceiptPostingOperatorPort currentOperator,
            ReceiptPostingOperationsAuthorizationPort authorization,
            JdbcReceiptPostingOperationsRepository postings,
            JdbcReceiptPostingRequeueAuditRepository audit,
            ReceiptPostingLedger ledger,
            Executor blockingExecutor) {
        this.currentOperator = Objects.requireNonNull(currentOperator, "currentOperator is required");
        this.authorization = Objects.requireNonNull(authorization, "authorization is required");
        this.postings = Objects.requireNonNull(postings, "postings is required");
        this.audit = Objects.requireNonNull(audit, "audit is required");
        this.ledger = Objects.requireNonNull(ledger, "ledger is required");
        this.blockingExecutor = Objects.requireNonNull(blockingExecutor, "blockingExecutor is required");
    }

    public Uni<ReceiptPostingOperationsSummary> summary() {
        return authorizeInspect().onItem().transformToUni(operator ->
                blocking(() -> postings.summary(operator.tenantId())));
    }

    public Uni<DeadLetterReceiptPostingPage> listDeadLetters(String cursor, int limit) {
        return authorizeInspect().onItem().transformToUni(operator ->
                blocking(() -> postings.findDeadLetters(operator.tenantId(), cursor, limit)));
    }

    public Uni<Optional<DeadLetterReceiptPostingDetail>> getDeadLetter(String receiptId, int historyLimit) {
        if (historyLimit < 1 || historyLimit > 200) {
            return failure(new IllegalArgumentException("historyLimit must be between 1 and 200"));
        }
        if (receiptId == null || receiptId.isBlank()) {
            return failure(new IllegalArgumentException("receiptId is required"));
        }
        return authorizeInspect().onItem().transformToUni(operator ->
                blocking(() -> postings.findDeadLetter(operator.tenantId(), receiptId)
                        .map(value -> new DeadLetterReceiptPostingDetail(value,
                                audit.findByReceipt(operator.tenantId(), receiptId, historyLimit)))));
    }

    public Uni<Void> requeue(String receiptId, String reason) {
        if (receiptId == null || receiptId.isBlank()) {
            return failure(new IllegalArgumentException("receiptId is required"));
        }
        if (reason == null || reason.isBlank()) {
            return failure(new IllegalArgumentException("reason is required"));
        }
        String normalizedReason = reason.trim();
        if (normalizedReason.length() > 2000) {
            return failure(new IllegalArgumentException("reason must not exceed 2000 characters"));
        }

        final ReceiptPostingOperatorContext operator;
        try {
            operator = requireCurrentOperator();
        } catch (RuntimeException unauthenticated) {
            return failure(unauthenticated);
        }

        return authorization.mayRequeue(operator, receiptId)
                .onItem().transformToUni(allowed -> {
                    if (!Boolean.TRUE.equals(allowed)) {
                        return failure(new SecurityException(
                                "Operator is not authorized to requeue this tenant's receipt posting"));
                    }
                    return blocking(() -> {
                        String fingerprint = postings.findFingerprint(operator.tenantId(), receiptId)
                                .orElseThrow(() -> new IllegalArgumentException(
                                        "Dead-letter receipt posting not found"));
                        ledger.requeue(operator.tenantId(), receiptId, fingerprint,
                                operator.actorId(), normalizedReason);
                        return Boolean.TRUE;
                    }).replaceWithVoid();
                });
    }

    private Uni<ReceiptPostingOperatorContext> authorizeInspect() {
        final ReceiptPostingOperatorContext operator;
        try {
            operator = requireCurrentOperator();
        } catch (RuntimeException unauthenticated) {
            return failure(unauthenticated);
        }
        return authorization.mayInspect(operator)
                .onItem().transformToUni(allowed -> Boolean.TRUE.equals(allowed)
                        ? Uni.createFrom().item(operator)
                        : failure(new SecurityException(
                                "Operator is not authorized to inspect receipt postings in this tenant")));
    }

    private ReceiptPostingOperatorContext requireCurrentOperator() {
        ReceiptPostingOperatorContext operator = currentOperator.current();
        if (operator == null) {
            throw new SecurityException("Authenticated receipt posting operator is required");
        }
        return operator;
    }

    private <T> Uni<T> blocking(Supplier<T> operation) {
        return Uni.createFrom().item(operation).runSubscriptionOn(blockingExecutor);
    }

    private static <T> Uni<T> failure(Throwable failure) {
        return Uni.createFrom().failure(failure);
    }
}

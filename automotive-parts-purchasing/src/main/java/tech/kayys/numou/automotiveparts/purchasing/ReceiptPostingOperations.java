package tech.kayys.numou.automotiveparts.purchasing;

import java.util.Objects;
import java.util.Optional;

/**
 * Application facade for tenant-scoped operator inspection and deliberate recovery.
 *
 * Tenant and actor are obtained from the host's trusted request context, never from API input.
 * The host must implement the authorizer using Numou Identity's effective permissions and
 * verified tenant membership.
 */
public final class ReceiptPostingOperations {
    public static final String INSPECT_PERMISSION = "automotive-parts.receipt-posting.inspect";
    public static final String REQUEUE_PERMISSION = "automotive-parts.receipt-posting.requeue";

    private final CurrentReceiptPostingOperatorPort currentOperator;
    private final ReceiptPostingOperationsAuthorizer authorizer;
    private final JdbcReceiptPostingOperationsRepository postings;
    private final JdbcReceiptPostingRequeueAuditRepository audit;
    private final RequeueReceiptPosting requeue;

    public ReceiptPostingOperations(CurrentReceiptPostingOperatorPort currentOperator,
                                    ReceiptPostingOperationsAuthorizer authorizer,
                                    JdbcReceiptPostingOperationsRepository postings,
                                    JdbcReceiptPostingRequeueAuditRepository audit,
                                    ReceiptPostingLedger ledger) {
        this.currentOperator = Objects.requireNonNull(currentOperator, "currentOperator is required");
        this.authorizer = Objects.requireNonNull(authorizer, "authorizer is required");
        this.postings = Objects.requireNonNull(postings, "postings is required");
        this.audit = Objects.requireNonNull(audit, "audit is required");
        this.requeue = new RequeueReceiptPosting(Objects.requireNonNull(ledger, "ledger is required"), authorizer);
    }

    public ReceiptPostingOperationsSummary summary() {
        ReceiptPostingOperatorContext operator = requireInspect();
        return postings.summary(operator.tenantId());
    }

    public DeadLetterReceiptPostingPage listDeadLetters(String cursor, int limit) {
        ReceiptPostingOperatorContext operator = requireInspect();
        return postings.findDeadLetters(operator.tenantId(), cursor, limit);
    }

    public Optional<DeadLetterReceiptPostingDetail> getDeadLetter(String receiptId, int historyLimit) {
        ReceiptPostingOperatorContext operator = requireInspect();
        if (historyLimit < 1 || historyLimit > 200) {
            throw new IllegalArgumentException("historyLimit must be between 1 and 200");
        }
        Optional<DeadLetterReceiptPosting> posting = postings.findDeadLetter(operator.tenantId(), receiptId);
        return posting.map(value -> new DeadLetterReceiptPostingDetail(value,
                audit.findByReceipt(operator.tenantId(), receiptId, historyLimit)));
    }

    public void requeue(String receiptId, String reason) {
        require(receiptId, "receiptId");
        require(reason, "reason");
        if (reason.trim().length() > 2000) throw new IllegalArgumentException("reason must not exceed 2000 characters");
        ReceiptPostingOperatorContext operator = current();
        if (!authorizer.mayRequeue(operator.tenantId(), operator.actorId(), receiptId)) {
            throw new SecurityException("Operator is not authorized to requeue this tenant's receipt posting");
        }
        String fingerprint = postings.findFingerprint(operator.tenantId(), receiptId)
                .orElseThrow(() -> new IllegalArgumentException("Dead-letter receipt posting not found"));
        requeue.execute(operator.tenantId(), receiptId, fingerprint, operator.actorId(), reason.trim());
    }

    private ReceiptPostingOperatorContext requireInspect() {
        ReceiptPostingOperatorContext operator = current();
        if (!authorizer.mayInspect(operator.tenantId(), operator.actorId())) {
            throw new SecurityException("Operator is not authorized to inspect receipt postings in this tenant");
        }
        return operator;
    }

    private ReceiptPostingOperatorContext current() {
        ReceiptPostingOperatorContext context = currentOperator.current();
        if (context == null) throw new SecurityException("Authenticated receipt posting operator is required");
        return context;
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
    }
}

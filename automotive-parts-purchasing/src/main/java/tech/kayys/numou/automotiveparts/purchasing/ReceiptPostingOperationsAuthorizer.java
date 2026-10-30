package tech.kayys.numou.automotiveparts.purchasing;

/**
 * Host authorization boundary for operator operations.
 *
 * Implementations must resolve grants from Numou Identity using the authenticated actor and
 * verified tenant membership; caller-supplied tenant/actor values are not trusted.
 */
public interface ReceiptPostingOperationsAuthorizer extends ReceiptPostingRequeueAuthorizer {
    boolean mayInspect(String tenantId, String actorId);

    @Override
    boolean mayRequeue(String tenantId, String actorId, String receiptId);
}

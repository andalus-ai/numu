package tech.kayys.numou.automotiveparts.purchasing;

/**
 * Host-application authorization boundary. Implementations must derive the actor's
 * permissions from trusted authentication and tenant membership, never caller-supplied claims.
 */
@FunctionalInterface
public interface ReceiptPostingRequeueAuthorizer {
    boolean mayRequeue(String tenantId, String actorId, String receiptId);
}

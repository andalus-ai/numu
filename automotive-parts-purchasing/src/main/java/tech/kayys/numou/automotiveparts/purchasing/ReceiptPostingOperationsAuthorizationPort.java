package tech.kayys.numou.automotiveparts.purchasing;

import io.smallrye.mutiny.Uni;

/**
 * Asynchronous host authorization boundary for operator operations.
 *
 * <p>Implementations should resolve effective permissions from the authenticated principal
 * and verified tenant using the host identity capability. They must not trust tenant,
 * actor, or permission values supplied by the HTTP caller.
 *
 * <p>The asynchronous contract allows a host to use reactive identity persistence without
 * blocking an event loop or waiting synchronously for a permission lookup.
 */
public interface ReceiptPostingOperationsAuthorizationPort {

    Uni<Boolean> mayInspect(ReceiptPostingOperatorContext operator);

    Uni<Boolean> mayRequeue(ReceiptPostingOperatorContext operator, String receiptId);
}

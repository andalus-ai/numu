package tech.kayys.numou.purchasing.spi;

/**
 * Transaction boundary for purchasing use cases that mutate more than one aggregate.
 *
 * <p>Production adapters must bind requisition, purchase-order, and receipt repositories
 * participating in a call to the same transaction context. Implementations must roll back
 * all enlisted writes if the action throws, and must not return before the action completes.
 * An adapter backed by independent stores must implement a durable workflow/outbox instead
 * of pretending a local transaction spans those stores.</p>
 */
@FunctionalInterface
public interface PurchasingUnitOfWork {
    void required(Runnable action);
}
package tech.kayys.numou.fulfillment.application;

import java.time.Instant;
import java.util.Optional;

/**
 * Durable saga store. Save must use optimistic version checks. beginOrLoad
 * must enforce tenant/workflow-key uniqueness and prevent a second active
 * allocation saga for the same order until the first reaches a terminal state.
 * Existing sagas retain their original reservation expiry across retries.
 */
public interface FulfillmentAllocationSagaRepository {
    FulfillmentAllocationSaga beginOrLoad(String tenantId, String workflowKey, String orderId,
                                           Instant reservationExpiresAt);

    Optional<FulfillmentAllocationSaga> find(String tenantId, String workflowKey);

    FulfillmentAllocationSaga save(FulfillmentAllocationSaga candidate, long expectedVersion);
}

package tech.kayys.numou.fulfillment.application;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

final class InMemoryFulfillmentAllocationSagaRepository implements FulfillmentAllocationSagaRepository {
    private final Map<String, FulfillmentAllocationSaga> sagas = new HashMap<>();

    @Override
    public synchronized FulfillmentAllocationSaga beginOrLoad(String tenantId, String workflowKey, String orderId,
                                                               Instant expiresAt) {
        String key = key(tenantId, workflowKey);
        FulfillmentAllocationSaga existing = sagas.get(key);
        if (existing != null) {
            if (!existing.orderId().equals(orderId)) throw new IllegalStateException("workflow key collision");
            return existing;
        }
        boolean active = sagas.values().stream().anyMatch(s -> s.tenantId().equals(tenantId)
                && s.orderId().equals(orderId)
                && s.status() != AllocationSagaStatus.COMPLETED
                && s.status() != AllocationSagaStatus.COMPENSATED);
        if (active) throw new IllegalStateException("another allocation saga is active");
        FulfillmentAllocationSaga created = FulfillmentAllocationSaga.start(tenantId, workflowKey, orderId, expiresAt);
        sagas.put(key, created);
        return created;
    }

    @Override
    public synchronized Optional<FulfillmentAllocationSaga> find(String tenantId, String workflowKey) {
        return Optional.ofNullable(sagas.get(key(tenantId, workflowKey)));
    }

    @Override
    public synchronized FulfillmentAllocationSaga save(FulfillmentAllocationSaga candidate, long expectedVersion) {
        String key = key(candidate.tenantId(), candidate.workflowKey());
        FulfillmentAllocationSaga current = sagas.get(key);
        if (current == null || current.version() != expectedVersion) {
            throw new FulfillmentConcurrencyException("Stale saga version");
        }
        if (candidate.version() != expectedVersion + 1) throw new IllegalArgumentException("Invalid saga version");
        sagas.put(key, candidate);
        return candidate;
    }

    private static String key(String tenant, String workflow) {
        return tenant.length() + ":" + tenant + workflow;
    }
}

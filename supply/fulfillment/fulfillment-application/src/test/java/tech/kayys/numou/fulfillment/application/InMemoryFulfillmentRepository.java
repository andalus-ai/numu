package tech.kayys.numou.fulfillment.application;

import tech.kayys.numou.fulfillment.domain.FulfillmentOrder;
import tech.kayys.numou.fulfillment.domain.FulfillmentOrderId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Test-only transactional model for exercising application contracts. */
final class InMemoryFulfillmentRepository implements FulfillmentRepository {
    private final Map<String, FulfillmentOrder> orders = new HashMap<>();
    private final Map<String, FulfillmentCommandReceipt> receipts = new HashMap<>();
    private final List<FulfillmentOutboxEvent> outbox = new ArrayList<>();

    @Override
    public synchronized Optional<FulfillmentOrder> find(String tenantId, FulfillmentOrderId orderId) {
        return Optional.ofNullable(orders.get(orderKey(tenantId, orderId.value())));
    }

    @Override
    public synchronized Optional<FulfillmentCommandReceipt> findReceipt(String tenantId, FulfillmentCommandKey key) {
        return Optional.ofNullable(receipts.get(receiptKey(tenantId, key)));
    }

    @Override
    public synchronized FulfillmentCommitResult commit(String tenantId, long expectedVersion,
                                                        FulfillmentOrder candidate, FulfillmentCommandKey key,
                                                        String fingerprint, List<FulfillmentOutboxEvent> events) {
        String receiptKey = receiptKey(tenantId, key);
        FulfillmentCommandReceipt existingReceipt = receipts.get(receiptKey);
        if (existingReceipt != null) {
            if (!existingReceipt.requestFingerprint().equals(fingerprint)) {
                throw new FulfillmentIdempotencyConflictException("key conflict");
            }
            return new FulfillmentCommitResult(existingReceipt.result(), true);
        }
        if (!tenantId.equals(candidate.tenantId())) throw new IllegalArgumentException("Tenant mismatch");
        String orderKey = orderKey(tenantId, candidate.id().value());
        FulfillmentOrder current = orders.get(orderKey);
        if (expectedVersion == 0) {
            if (current != null) throw new FulfillmentConcurrencyException("Order already exists");
        } else if (current == null || current.version() != expectedVersion) {
            throw new FulfillmentConcurrencyException("Stale aggregate version");
        }
        if (candidate.version() != expectedVersion + 1) {
            throw new IllegalArgumentException("Candidate version must increment exactly once");
        }
        for (FulfillmentOutboxEvent event : events) {
            if (!tenantId.equals(event.tenantId()) || !candidate.id().value().equals(event.aggregateId())
                    || event.aggregateVersion() != candidate.version()) {
                throw new IllegalArgumentException("Outbox event does not match committed aggregate");
            }
        }
        FulfillmentCommandReceipt receipt = new FulfillmentCommandReceipt(tenantId, key, fingerprint,
                candidate, java.time.Instant.now());
        orders.put(orderKey, candidate);
        receipts.put(receiptKey, receipt);
        outbox.addAll(events);
        return new FulfillmentCommitResult(candidate, false);
    }

    synchronized List<FulfillmentOutboxEvent> outbox() {
        return List.copyOf(outbox);
    }

    private static String orderKey(String tenant, String order) {
        return tenant.length() + ":" + tenant + order;
    }

    private static String receiptKey(String tenant, FulfillmentCommandKey key) {
        return tenant.length() + ":" + tenant + key.value();
    }
}

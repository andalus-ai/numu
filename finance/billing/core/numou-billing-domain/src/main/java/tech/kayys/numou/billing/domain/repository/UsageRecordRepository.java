package tech.kayys.numou.billing.domain.repository;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.billing.domain.identifier.UsageRecordId;
import tech.kayys.numou.billing.domain.model.UsageRecord;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UsageRecordRepository {
    Uni<UsageRecord> save(UsageRecord record);
    Uni<Optional<UsageRecord>> findById(UsageRecordId id);
    Uni<Boolean> existsById(UsageRecordId id);
    Uni<Void> delete(UsageRecord record);
    Uni<Void> deleteById(UsageRecordId id);
    Uni<List<UsageRecord>> findByCustomerId(String customerId);
    Uni<List<UsageRecord>> findBySubscriptionId(String subscriptionId);
    Uni<List<UsageRecord>> findUninvoicedUsage(String customerId);
    Uni<List<UsageRecord>> findByDateRange(Instant start, Instant end);
    Uni<Double> getTotalUsage(String customerId, String meterId, Instant start, Instant end);
}

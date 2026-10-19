package tech.kayys.numou.billing.domain.repository;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.billing.domain.identifier.BillingScheduleId;
import tech.kayys.numou.billing.domain.model.BillingSchedule;
import tech.kayys.numou.billing.domain.valueobject.BillingStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BillingScheduleRepository {
    Uni<BillingSchedule> save(BillingSchedule schedule);
    Uni<Optional<BillingSchedule>> findById(BillingScheduleId id);
    Uni<Boolean> existsById(BillingScheduleId id);
    Uni<Void> delete(BillingSchedule schedule);
    Uni<Void> deleteById(BillingScheduleId id);
    Uni<Optional<BillingSchedule>> findBySubscriptionId(UUID subscriptionId);
    Uni<List<BillingSchedule>> findByCustomerId(String customerId);
    Uni<List<BillingSchedule>> findByStatus(BillingStatus status);
    Uni<List<BillingSchedule>> findDueSchedules();
    Uni<List<BillingSchedule>> findSchedulesWithPaymentFailures();
    Uni<List<BillingSchedule>> findUpcomingBilling(int daysAhead);
    Uni<List<BillingSchedule>> findExpiredSchedules();
    Uni<BillingSchedule> updateStatus(UUID scheduleId, BillingStatus newStatus);
}

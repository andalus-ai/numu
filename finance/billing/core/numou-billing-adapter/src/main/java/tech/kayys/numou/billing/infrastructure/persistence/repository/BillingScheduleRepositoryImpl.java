package tech.kayys.numou.billing.infrastructure.persistence.repository;

import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple2;
import tech.kayys.numou.billing.domain.identifier.BillingScheduleId;
import tech.kayys.numou.billing.domain.model.BillingSchedule;
import tech.kayys.numou.billing.domain.repository.BillingScheduleRepository;
import tech.kayys.numou.billing.domain.valueobject.BillingStatus;
import tech.kayys.numou.billing.infrastructure.persistence.entity.BillingCycleEntity;
import tech.kayys.numou.billing.infrastructure.persistence.entity.BillingScheduleEntity;
import tech.kayys.numou.billing.application.api.query.BillingStatistics;
import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.foundation.persistence.BaseRepository;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Reactive repository implementation for BillingSchedule.
 */
@ApplicationScoped
public class BillingScheduleRepositoryImpl extends BaseRepository<BillingScheduleEntity> 
        implements BillingScheduleRepository {

    /**
     * Inserts a new schedule or updates a stored one, together with its cycles, in one transaction. (The id is
     * always set, so "has an id" cannot tell the two apart; the stored row decides. The version column belongs to
     * Hibernate and is never copied from the domain object.)
     */
    @Override
    @WithTransaction
    public Uni<BillingSchedule> save(BillingSchedule schedule) {
        BillingScheduleEntity entity = BillingScheduleEntity.fromDomain(schedule);

        return super.findById(entity.id)
            .chain(existing -> {
                Uni<BillingScheduleEntity> stored;
                if (existing == null) {
                    entity.version = null;
                    stored = persist(entity);
                } else {
                    existing.subscriptionId = entity.subscriptionId;
                    existing.customerId = entity.customerId;
                    existing.customerEmail = entity.customerEmail;
                    existing.frequency = entity.frequency;
                    existing.status = entity.status;
                    existing.startDate = entity.startDate;
                    existing.endDate = entity.endDate;
                    existing.nextBillingDate = entity.nextBillingDate;
                    existing.lastBillingDate = entity.lastBillingDate;
                    existing.amount = entity.amount;
                    existing.currency = entity.currency;
                    existing.paymentMethodToken = entity.paymentMethodToken;
                    existing.currentCycle = entity.currentCycle;
                    existing.totalCycles = entity.totalCycles;
                    existing.failedPaymentCount = entity.failedPaymentCount;
                    existing.maxFailedPayments = entity.maxFailedPayments;
                    existing.sendEmailNotifications = entity.sendEmailNotifications;
                    existing.sendSmsNotifications = entity.sendSmsNotifications;
                    existing.active = entity.active;
                    existing.updatedAt = entity.updatedAt;
                    stored = persist(existing);
                }
                return stored.chain(v -> saveCycles(schedule)).map(v -> {
                    schedule.clearEvents();
                    return schedule;
                });
            });
    }

    /** Writes each cycle of the schedule: a new one is inserted, a known one takes its current state. */
    private Uni<Void> saveCycles(BillingSchedule schedule) {
        UUID scheduleId = schedule.getId().getValue();
        return BillingCycleEntity.<BillingCycleEntity>find("scheduleId = ?1", scheduleId).list()
            .chain(rows -> {
                Map<Integer, BillingCycleEntity> byNumber = new HashMap<>();
                rows.forEach(r -> byNumber.put(r.cycleNumber, r));
                Uni<Void> chain = Uni.createFrom().voidItem();
                for (BillingSchedule.BillingCycle cycle : schedule.getBillingCycles()) {
                    BillingCycleEntity row = byNumber.get(cycle.getCycleNumber());
                    if (row == null) {
                        chain = chain.chain(() -> BillingCycleEntity.fromDomain(scheduleId, cycle).persist()
                            .replaceWithVoid());
                    } else {
                        row.copyFrom(cycle);
                        chain = chain.chain(() -> row.persist().replaceWithVoid());
                    }
                }
                return chain;
            });
    }

    /** Builds domain schedules from stored rows, with their cycles (one query for all of them). */
    private Uni<List<BillingSchedule>> hydrate(List<BillingScheduleEntity> entities) {
        if (entities.isEmpty()) {
            return Uni.createFrom().item(List.of());
        }
        List<UUID> ids = entities.stream().map(e -> e.id).collect(Collectors.toList());
        return BillingCycleEntity.<BillingCycleEntity>find("scheduleId in ?1", ids).list()
            .map(rows -> {
                Map<UUID, List<BillingSchedule.BillingCycle>> bySchedule = new HashMap<>();
                rows.forEach(r -> bySchedule.computeIfAbsent(r.scheduleId, k -> new ArrayList<>()).add(r.toDomain()));
                return entities.stream().map(e -> {
                    BillingSchedule schedule = e.toDomain();
                    schedule.restoreCycles(bySchedule.getOrDefault(e.id, List.of()));
                    return schedule;
                }).collect(Collectors.toList());
            });
    }

    @Override
    @WithSession
    public Uni<Optional<BillingSchedule>> findById(BillingScheduleId id) {
        return this.<BillingScheduleEntity>findById(id.getValue())
            .chain(entity -> entity == null ? Uni.createFrom().item(Optional.<BillingSchedule>empty())
                : hydrate(List.of(entity)).map(list -> Optional.of(list.get(0))));
    }

    @Override
    @WithSession
    public Uni<Boolean> existsById(BillingScheduleId id) {
        return findById(id)
            .onItem()
            .transform(opt -> opt.isPresent());
    }

    @Override
    @WithTransaction
    public Uni<Void> delete(BillingSchedule schedule) {
        return deleteById(schedule.getId().getValue())
            .onItem()
            .transform(v -> null);
    }

    @Override
    @WithTransaction
    public Uni<Void> deleteById(BillingScheduleId id) {
        return deleteById(id.getValue())
            .onItem()
            .transform(v -> null);
    }

    @Override
    @WithSession
    public Uni<Optional<BillingSchedule>> findBySubscriptionId(UUID subscriptionId) {
        return find("subscriptionId = ?1", subscriptionId)
            .firstResult()
            .chain(entity -> entity == null ? Uni.createFrom().item(Optional.<BillingSchedule>empty())
                : hydrate(List.of(entity)).map(list -> Optional.of(list.get(0))));
    }

    @Override
    @WithSession
    public Uni<List<BillingSchedule>> findByCustomerId(String customerId) {
        return find("customerId = ?1 order by startDate desc", customerId)
            .list()
            .chain(this::hydrate);
    }

    @Override
    @WithSession
    public Uni<List<BillingSchedule>> findByStatus(BillingStatus status) {
        return find("status = ?1 order by nextBillingDate asc", status.name())
            .list()
            .chain(this::hydrate);
    }

    @Override
    @WithSession
    public Uni<List<BillingSchedule>> findDueSchedules() {
        Instant now = Instant.now();
        return find("status = 'ACTIVE' and nextBillingDate <= ?1 order by nextBillingDate asc", now)
            .list()
            .chain(this::hydrate);
    }

    @Override
    @WithSession
    public Uni<List<BillingSchedule>> findSchedulesWithPaymentFailures() {
        return find("status = 'ACTIVE' and failedPaymentCount > 0 order by failedPaymentCount desc")
            .list()
            .chain(this::hydrate);
    }

    @Override
    @WithSession
    public Uni<List<BillingSchedule>> findUpcomingBilling(int daysAhead) {
        Instant now = Instant.now();
        Instant future = now.plusSeconds(daysAhead * 24L * 60L * 60L);
        return find("status = 'ACTIVE' and nextBillingDate between ?1 and ?2 order by nextBillingDate asc", now, future)
            .list()
            .chain(this::hydrate);
    }

    public Uni<BillingStatistics> getStatistics(Instant fromDate, Instant toDate) {
        // This is a complex query - simplified for demonstration
        return count("status = 'ACTIVE'")
            .chain(activeCount -> {
                // Get total due amount
                return find("status = 'ACTIVE' and nextBillingDate between ?1 and ?2", fromDate, toDate)
                    .list()
                    .onItem()
                    .transform(entities -> {
                        long totalActive = activeCount;
                        Money totalDue = entities.stream()
                            .map(entity -> Money.of(entity.amount, entity.currency))
                            .reduce(Money.zero("USD"), (m1, m2) -> m1.add(m2));
                        
                        // For simplicity, assume all due are collected
                        Money totalCollected = totalDue;
                        
                        return new BillingStatistics(
                            fromDate,
                            toDate,
                            totalActive,
                            totalDue,
                            totalCollected,
                            0, // failed payments
                            100.0, // success rate
                            totalDue.multiply(java.math.BigDecimal.valueOf(0.1)) // average revenue
                        );
                    });
            });
    }

    /**
     * Finds expired billing schedules.
     */
    public Uni<List<BillingSchedule>> findExpiredSchedules() {
        Instant now = Instant.now();
        return find("status = 'ACTIVE' and endDate is not null and endDate <= ?1", now)
            .list()
            .chain(this::hydrate);
    }

    /**
     * Updates billing schedule status.
     */
    @WithTransaction
    public Uni<BillingSchedule> updateStatus(UUID scheduleId, BillingStatus newStatus) {
        return super.findById(scheduleId)
            .chain(entity -> {
                if (entity == null) {
                    return Uni.createFrom().failure(
                        new IllegalArgumentException("Billing schedule not found: " + scheduleId)
                    );
                }
                entity.status = newStatus.name();
                entity.updatedAt = Instant.now();
                return persist(entity)
                    .onItem()
                    .transform(BillingScheduleEntity::toDomain);
            });
    }
}
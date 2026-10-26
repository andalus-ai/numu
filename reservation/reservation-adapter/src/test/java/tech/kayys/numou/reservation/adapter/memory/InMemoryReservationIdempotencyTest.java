package tech.kayys.numou.reservation.adapter.memory;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.reservation.domain.*;
import tech.kayys.numou.reservation.spi.IdempotentReservationRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryReservationIdempotencyTest {

    @Test
    void replayReturnsOriginalReservationWithoutCreatingAnother() {
        var repository = new InMemoryReservationRepositories.Reservations();
        var tenant = UUID.randomUUID();
        var candidate = candidate();
        var first = repository.saveIdempotently(tenant, "request-1", "fingerprint-a", candidate)
                .await().indefinitely();
        var replayCandidate = candidate();

        var replay = repository.saveIdempotently(tenant, "request-1", "fingerprint-a", replayCandidate)
                .await().indefinitely();

        assertThat(first.created()).isTrue();
        assertThat(replay.created()).isFalse();
        assertThat(replay.reservation().id()).isEqualTo(first.reservation().id());
        assertThat(repository.allOf(tenant)).hasSize(1);
    }

    @Test
    void rejectsKeyReuseWithDifferentFingerprint() {
        var repository = new InMemoryReservationRepositories.Reservations();
        var tenant = UUID.randomUUID();
        repository.saveIdempotently(tenant, "request-1", "fingerprint-a", candidate())
                .await().indefinitely();

        assertThatThrownBy(() -> repository.saveIdempotently(tenant, "request-1", "fingerprint-b", candidate())
                .await().indefinitely())
                .hasCauseInstanceOf(IdempotentReservationRepository.IdempotencyKeyConflictException.class);
        assertThat(repository.allOf(tenant)).hasSize(1);
    }

    @Test
    void idempotencyKeysAreTenantScoped() {
        var repository = new InMemoryReservationRepositories.Reservations();
        var firstTenant = UUID.randomUUID();
        var secondTenant = UUID.randomUUID();
        var first = repository.saveIdempotently(firstTenant, "same-key", "same-fingerprint", candidate())
                .await().indefinitely();
        var second = repository.saveIdempotently(secondTenant, "same-key", "same-fingerprint", candidate())
                .await().indefinitely();

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isTrue();
        assertThat(first.reservation().id()).isNotEqualTo(second.reservation().id());
    }

    @Test
    void concurrentRetriesCreateOnlyOneReservation() throws Exception {
        var repository = new InMemoryReservationRepositories.Reservations();
        var tenant = UUID.randomUUID();
        try (var executor = Executors.newFixedThreadPool(8)) {
            var ready = new CountDownLatch(8);
            var start = new CountDownLatch(1);
            var futures = java.util.stream.IntStream.range(0, 8).mapToObj(i -> executor.submit(() -> {
                ready.countDown();
                start.await();
                return repository.saveIdempotently(tenant, "concurrent-key", "same-fingerprint", candidate())
                        .await().indefinitely();
            })).toList();
            ready.await();
            start.countDown();
            var results = new java.util.ArrayList<IdempotentReservationRepository.IdempotentSaveResult>();
            for (var future : futures) results.add(future.get());
            assertThat(results).filteredOn(IdempotentReservationRepository.IdempotentSaveResult::created).hasSize(1);
            assertThat(results).extracting(result -> result.reservation().id()).doesNotHaveDuplicates();
            assertThat(repository.allOf(tenant)).hasSize(1);
        }
    }

    private static Reservation candidate() {
        var now = Instant.parse("2026-10-09T06:00:00Z");
        var rules = new BookingRules(java.time.ZoneId.of("Asia/Jakarta"), 1, 10, 30,
                Duration.ofMinutes(30), Duration.ofMinutes(60), Duration.ofMinutes(15),
                java.util.Map.of(2, Duration.ofMinutes(60)), 4,
                tech.kayys.numou.foundation.domain.valueobject.Money.of(100_000, "IDR"),
                Duration.ofHours(24),
                tech.kayys.numou.foundation.domain.valueobject.Money.of(50_000, "IDR"),
                Duration.ofMinutes(15), Duration.ofMinutes(15),
                java.util.Map.of(java.time.DayOfWeek.FRIDAY, java.util.List.of(
                        new OperatingWindow("Day", java.time.LocalTime.of(8, 0), java.time.LocalTime.of(22, 0),
                                java.time.LocalTime.of(20, 0)))));
        var startsAt = Instant.parse("2026-10-09T07:00:00Z");
        return Reservation.request(ReservationId.generate(), FacilityId.of(UUID.randomUUID()),
                new CustomerContact("Test", "+628111", "test@example.com"), PartySize.of(2), startsAt,
                BookingSource.WEB, "idempotency test", Set.of(), Duration.ofMinutes(60), rules, now);
    }
}

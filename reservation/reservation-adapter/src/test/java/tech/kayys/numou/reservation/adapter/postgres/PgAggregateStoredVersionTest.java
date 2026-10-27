package tech.kayys.numou.reservation.adapter.postgres;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.PreparedQuery;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.SqlConnection;
import io.vertx.mutiny.sqlclient.Tuple;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.reservation.domain.AvailabilityLedger;
import tech.kayys.numou.reservation.domain.BookingRules;
import tech.kayys.numou.reservation.domain.BookingSource;
import tech.kayys.numou.reservation.domain.CustomerContact;
import tech.kayys.numou.reservation.domain.FacilityId;
import tech.kayys.numou.reservation.domain.PartySize;
import tech.kayys.numou.reservation.domain.Reservation;
import tech.kayys.numou.reservation.domain.ReservationId;

import java.lang.reflect.Proxy;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Regression tests for acknowledging aggregate versions only after a successful SQL write. */
class PgAggregateStoredVersionTest {

    private static final Instant NOW = Instant.parse("2026-10-09T10:00:00Z");
    private static final ZoneId ZONE = ZoneId.of("Asia/Jakarta");

    @Test
    void reservationWriteFailureDoesNotAdvanceStoredVersion() {
        Reservation reservation = reservation();
        PgReservationRepository repository = new PgReservationRepository(connectionFailingOnExecute());

        assertThatThrownBy(() -> repository.save(java.util.UUID.randomUUID(), reservation).await().indefinitely())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("simulated database write failure");

        assertThat(reservation.storedVersion()).isZero();
    }

    @Test
    void reservationOptimisticConflictDoesNotAdvanceStoredVersion() {
        Reservation reservation = reservation();
        PgReservationRepository repository = new PgReservationRepository(connectionReturningRowCount(0));

        assertThatThrownBy(() -> repository.save(java.util.UUID.randomUUID(), reservation).await().indefinitely())
                .isInstanceOf(PgReservationRepository.OptimisticReservationWriteException.class);

        assertThat(reservation.storedVersion()).isZero();
    }

    @Test
    void ledgerWriteFailureDoesNotAdvanceStoredVersion() {
        AvailabilityLedger ledger = AvailabilityLedger.open(
                FacilityId.generate(), LocalDate.of(2026, 10, 9), NOW);
        PgLedgerRepository repository = new PgLedgerRepository(connectionFailingOnExecute());

        assertThatThrownBy(() -> repository.save(java.util.UUID.randomUUID(), ledger).await().indefinitely())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("simulated database write failure");

        assertThat(ledger.storedVersion()).isZero();
    }

    @Test
    void ledgerOptimisticConflictDoesNotAdvanceStoredVersion() {
        AvailabilityLedger ledger = AvailabilityLedger.open(
                FacilityId.generate(), LocalDate.of(2026, 10, 9), NOW);
        PgLedgerRepository repository = new PgLedgerRepository(connectionReturningRowCount(0));

        assertThatThrownBy(() -> repository.save(java.util.UUID.randomUUID(), ledger).await().indefinitely())
                .isInstanceOf(PgReservationRepository.OptimisticReservationWriteException.class);

        assertThat(ledger.storedVersion()).isZero();
    }

    private static Reservation reservation() {
        FacilityId facility = FacilityId.generate();
        BookingRules rules = new BookingRules(
                ZONE, 1, 12, 30,
                Duration.ofMinutes(60), Duration.ofMinutes(90), Duration.ofMinutes(15),
                Map.of(2, Duration.ofMinutes(90), 6, Duration.ofMinutes(120)),
                5, Money.of(100_000, "IDR"),
                Duration.ofHours(24), Money.of(150_000, "IDR"),
                Duration.ofMinutes(15), Duration.ofMinutes(15),
                Map.of(DayOfWeek.FRIDAY, List.of(new tech.kayys.numou.reservation.domain.OperatingWindow(
                        "Dinner", LocalTime.of(17, 0), LocalTime.of(23, 0), LocalTime.of(21, 30)))));
        Instant start = LocalDateTime.of(2026, 10, 9, 18, 0).atZone(ZONE).toInstant();
        return Reservation.request(ReservationId.generate(), facility,
                new CustomerContact("Test Guest", "+62812345678", "test@example.com"),
                PartySize.of(2), start, BookingSource.WEB, "", Set.of(), rules, NOW);
    }

    private static SqlConnection connectionFailingOnExecute() {
        return connectionWithExecute(() -> Uni.createFrom().failure(
                new IllegalStateException("simulated database write failure")));
    }

    private static SqlConnection connectionReturningRowCount(int count) {
        RowSet<Row> rows = proxy(RowSet.class, (method, args) -> {
            if (method.equals("rowCount")) return count;
            if (method.equals("iterator")) return List.<Row>of().iterator();
            return defaultValue(method);
        });
        return connectionWithExecute(() -> Uni.createFrom().item(rows));
    }

    private static SqlConnection connectionWithExecute(java.util.function.Supplier<Uni<RowSet<Row>>> execute) {
        PreparedQuery<Row> query = proxy(PreparedQuery.class, (method, args) -> {
            if (method.equals("execute")) return execute.get();
            return defaultValue(method);
        });
        return proxy(SqlConnection.class, (method, args) -> {
            if (method.equals("preparedQuery")) return query;
            return defaultValue(method);
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<?> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (instance, method, args) -> invocation.invoke(method.getName(), args));
    }

    private static Object defaultValue(String method) {
        return switch (method) {
            case "toString" -> "test-proxy";
            case "hashCode" -> System.identityHashCode(PgAggregateStoredVersionTest.class);
            case "equals" -> false;
            default -> null;
        };
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(String method, Object[] args);
    }
}

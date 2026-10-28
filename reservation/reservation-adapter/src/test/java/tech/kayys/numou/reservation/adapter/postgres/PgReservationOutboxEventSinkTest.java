package tech.kayys.numou.reservation.adapter.postgres;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.PreparedQuery;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.SqlConnection;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.reservation.domain.event.ReservationRequested;
import tech.kayys.numou.reservation.spi.DomainEventSink;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Contract tests for sequential, connection-bound reservation outbox writes. */
class PgReservationOutboxEventSinkTest {

    @Test
    void writesEachEventSequentiallyOnTheSuppliedConnection() {
        AtomicInteger preparedQueries = new AtomicInteger();
        AtomicInteger executes = new AtomicInteger();
        RowSet<Row> rows = proxy(RowSet.class, (method, args) -> defaultValue(method));
        PreparedQuery<RowSet<Row>> query = proxy(PreparedQuery.class, (method, args) -> {
            if (method.equals("execute")) {
                executes.incrementAndGet();
                return Uni.createFrom().item(rows);
            }
            return defaultValue(method);
        });
        SqlConnection connection = proxy(SqlConnection.class, (method, args) -> {
            if (method.equals("preparedQuery")) {
                preparedQueries.incrementAndGet();
                return query;
            }
            return defaultValue(method);
        });

        UUID tenant = UUID.randomUUID();
        UUID reservation = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-10-09T10:00:00Z");
        DomainEventSink sink = new PgReservationOutboxEventSink(connection);
        sink.publish(tenant, List.of(
                ReservationRequested.at(occurredAt, reservation, UUID.randomUUID(), 2,
                        occurredAt.plusSeconds(3600), "PENDING"),
                ReservationRequested.at(occurredAt.plusSeconds(1), reservation, UUID.randomUUID(), 2,
                        occurredAt.plusSeconds(7200), "PENDING")
        )).await().indefinitely();

        assertThat(preparedQueries).hasValue(2);
        assertThat(executes).hasValue(2);
    }

    @Test
    void rejectsUnsupportedAggregatePayloadBeforeIssuingSql() {
        AtomicInteger executes = new AtomicInteger();
        PreparedQuery<RowSet<Row>> query = proxy(PreparedQuery.class, (method, args) -> {
            if (method.equals("execute")) {
                executes.incrementAndGet();
                return Uni.createFrom().item(proxy(RowSet.class, (m, a) -> defaultValue(m)));
            }
            return defaultValue(method);
        });
        SqlConnection connection = proxy(SqlConnection.class, (method, args) ->
                method.equals("preparedQuery") ? query : defaultValue(method));

        var invalidEvent = new ReservationRequested(UUID.randomUUID(), Instant.now(),
                null, UUID.randomUUID(), 2, Instant.now().plusSeconds(3600), "PENDING");
        assertThatThrownBy(() -> new PgReservationOutboxEventSink(connection)
                .publish(UUID.randomUUID(), List.of(invalidEvent)).await().indefinitely())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing reservationId");
        assertThat(executes).hasValue(0);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<?> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (instance, method, args) -> invocation.invoke(method.getName(), args));
    }

    private static Object defaultValue(String method) {
        return switch (method) {
            case "toString" -> "test-proxy";
            case "hashCode" -> System.identityHashCode(PgReservationOutboxEventSinkTest.class);
            case "equals" -> false;
            default -> null;
        };
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(String method, Object[] args);
    }
}

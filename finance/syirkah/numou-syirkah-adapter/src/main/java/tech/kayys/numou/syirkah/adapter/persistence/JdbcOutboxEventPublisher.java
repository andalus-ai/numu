package tech.kayys.numou.numouadapter.persistence;

import tech.kayys.numou.foundation.domain.identifier.DomainId;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.numouapplication.port.EventPublisherPort;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.util.*;

/**
 * Transactional outbox writer (V301): events are inserted on the unit-of-work
 * connection, so they commit or roll
 * back with the ledger change. A relay (not part of this module) ships rows
 * with {@code published_at IS NULL} in
 * {@code seq} order. Re-publishing the same event id is a no-op.
 *
 * <p>
 * Event records are serialised generically from their components: ids as
 * strings, {@link Money} as
 * {amount, currency}, enums by name, instants/dates ISO-8601.
 */
public class JdbcOutboxEventPublisher implements EventPublisherPort {

    private final JdbcUnitOfWork uow;

    public JdbcOutboxEventPublisher(JdbcUnitOfWork uow) {
        this.uow = Objects.requireNonNull(uow, "uow");
    }

    @Override
    public void publish(TenantId tenant, List<DomainEvent> events) {
        if (events.isEmpty()) {
            return;
        }
        JdbcSupport.inConnection(uow, tenant, c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO syirkah_ledger_outbox (tenant_id, event_id, event_type, syirkah_id, occurred_at,"
                            + " payload) VALUES (?,?,?,?,?,?::jsonb) ON CONFLICT (tenant_id, event_id) DO NOTHING")) {
                for (DomainEvent e : events) {
                    Map<String, Object> body = toMap(e);
                    ps.setObject(1, tenant.value());
                    ps.setObject(2, e.eventId());
                    ps.setString(3, e.eventType());
                    Object sid = body.get("syirkahId");
                    ps.setObject(4, sid == null ? null : UUID.fromString((String) sid));
                    ps.setObject(5, JdbcSupport.ts(e.occurredAt()));
                    ps.setString(6, Json.write(body));
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            return null;
        });
    }

    static Map<String, Object> toMap(Object record) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (RecordComponent rc : record.getClass().getRecordComponents()) {
            try {
                out.put(rc.getName(), value(rc.getAccessor().invoke(record)));
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException("cannot serialise " + record.getClass().getName(), ex);
            }
        }
        return out;
    }

    private static Object value(Object v) {
        if (v == null || v instanceof String || v instanceof Boolean || v instanceof BigDecimal) {
            return v;
        }
        if (v instanceof Integer || v instanceof Long) {
            return v;
        }
        if (v instanceof Money m) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("amount", m.amount());
            o.put("currency", m.currency().code());
            return o;
        }
        if (v instanceof DomainId<?> id) {
            return String.valueOf(id.value());
        }
        if (v instanceof Enum<?> || v instanceof UUID || v instanceof java.time.temporal.TemporalAccessor) {
            return v instanceof Enum<?> e ? e.name() : v.toString();
        }
        if (v instanceof Collection<?> c) {
            List<Object> l = new ArrayList<>();
            c.forEach(x -> l.add(value(x)));
            return l;
        }
        if (v.getClass().isRecord()) {
            return toMap(v);
        }
        throw new IllegalArgumentException("unsupported event field type " + v.getClass().getName());
    }
}

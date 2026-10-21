package tech.kayys.numou.numouadapter.memory;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.numouapplication.ops.OutboxRecord;
import tech.kayys.numou.numouapplication.port.EventPublisherPort;
import tech.kayys.numou.numouapplication.port.OutboxPort;
import tech.kayys.numou.numoudomain.SyirkahId;

import java.time.Instant;
import java.util.*;

/**
 * Reference outbox (publisher and relay side) for tests and single-node use;
 * idempotent per event id.
 */
public class InMemoryOutbox implements EventPublisherPort, OutboxPort {

    private record Row(TenantId tenant, OutboxRecord record, Instant[] publishedAt) {
    }

    private final List<Row> rows = new ArrayList<>();
    private final Set<String> seen = new HashSet<>();
    private long next = 1;

    @Override
    public synchronized void publish(TenantId tenant, List<DomainEvent> events) {
        for (DomainEvent e : events) {
            if (!seen.add(tenant.value() + "/" + e.eventId())) {
                continue;
            }
            SyirkahId sid = null;
            try {
                Object v = e.getClass().getMethod("syirkahId").invoke(e);
                sid = v instanceof SyirkahId id ? id : null;
            } catch (ReflectiveOperationException ignored) {
                // event without a syirkah id
            }
            rows.add(new Row(tenant, new OutboxRecord(next++, e.eventId(), e.eventType(), sid, e.occurredAt(),
                    e.toString()), new Instant[1]));
        }
    }

    @Override
    public synchronized List<OutboxRecord> claim(TenantId tenant, int limit) {
        return rows.stream().filter(r -> r.tenant().equals(tenant) && r.publishedAt()[0] == null)
                .limit(limit).map(Row::record).toList();
    }

    @Override
    public synchronized void markPublished(TenantId tenant, List<Long> sequences, Instant at) {
        for (Row r : rows) {
            if (r.tenant().equals(tenant) && sequences.contains(r.record().sequence())
                    && r.publishedAt()[0] == null) {
                r.publishedAt()[0] = at;
            }
        }
    }

    public synchronized int unpublishedCount(TenantId tenant) {
        return (int) rows.stream().filter(r -> r.tenant().equals(tenant) && r.publishedAt()[0] == null).count();
    }
}

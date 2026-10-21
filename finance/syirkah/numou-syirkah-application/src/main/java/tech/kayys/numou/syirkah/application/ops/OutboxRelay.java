package tech.kayys.numou.numouapplication.ops;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.numouapplication.port.OutboxPort;
import tech.kayys.numou.numouapplication.port.TransactionPort;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Ships outbox events to a sink (message broker, webhook, ...). Delivery is
 * at-least-once: events are marked
 * published only after the sink accepted them, in the same transaction that
 * holds their row locks. If the sink fails
 * on an event, everything delivered before it is still marked and the failure
 * is rethrown, so the failed event is the
 * next one retried and order is kept. A crash between delivery and commit
 * re-delivers, hence consumers must
 * de-duplicate on {@code eventId}. Run one relay per tenant at a time if strict
 * global order matters.
 */
public class OutboxRelay {

    private final OutboxPort outbox;
    private final TransactionPort tx;
    private final Clock clock;

    public OutboxRelay(OutboxPort outbox, TransactionPort tx, Clock clock) {
        this.outbox = Objects.requireNonNull(outbox);
        this.tx = Objects.requireNonNull(tx);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * Delivers one batch and returns how many events were shipped (0 when the
     * outbox is drained).
     */
    public int drain(TenantId tenant, int batchSize, Consumer<OutboxRecord> sink) {
        if (batchSize < 1 || batchSize > 1000) {
            throw new IllegalArgumentException("batchSize must be 1..1000");
        }
        Objects.requireNonNull(sink, "sink");
        RuntimeException[] failure = new RuntimeException[1];
        int delivered = tx.inTransaction(() -> {
            List<Long> done = new ArrayList<>();
            for (OutboxRecord r : outbox.claim(tenant, batchSize)) {
                try {
                    sink.accept(r);
                } catch (RuntimeException e) {
                    failure[0] = e;
                    break;
                }
                done.add(r.sequence());
            }
            if (!done.isEmpty()) {
                outbox.markPublished(tenant, done, clock.instant().truncatedTo(ChronoUnit.MICROS));
            }
            return done.size();
        });
        if (failure[0] != null) {
            throw failure[0];
        }
        return delivered;
    }

    /**
     * Drains until empty or {@code maxBatches} reached; returns the total shipped.
     */
    public int drainAll(TenantId tenant, int batchSize, int maxBatches, Consumer<OutboxRecord> sink) {
        int total = 0;
        for (int i = 0; i < maxBatches; i++) {
            int n = drain(tenant, batchSize, sink);
            total += n;
            if (n < batchSize) {
                break;
            }
        }
        return total;
    }
}

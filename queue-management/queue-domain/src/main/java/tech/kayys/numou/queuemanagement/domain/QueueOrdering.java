package tech.kayys.numou.queuemanagement.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Pure deterministic ordering for waiting entries from one already tenant/queue-scoped query. */
public final class QueueOrdering {
    private QueueOrdering() {}

    public static List<QueueEntry> orderWaiting(List<QueueEntry> entries, QueueOrderingPolicy policy) {
        Objects.requireNonNull(entries, "entries must not be null");
        Objects.requireNonNull(policy, "policy must not be null");
        Comparator<QueueEntry> comparator = Comparator.comparing(QueueEntry::enteredAt)
                .thenComparing(entry -> entry.id().value().toString());
        if (policy == QueueOrderingPolicy.PRIORITY_THEN_ARRIVAL) {
            comparator = Comparator.comparingInt(QueueEntry::priority).reversed()
                    .thenComparing(QueueEntry::enteredAt)
                    .thenComparing(entry -> entry.id().value().toString());
        }
        List<QueueEntry> ordered = new ArrayList<>();
        for (QueueEntry entry : entries) {
            if (entry != null && entry.status() == QueueEntryStatus.WAITING) {
                ordered.add(entry);
            }
        }
        ordered.sort(comparator);
        return List.copyOf(ordered);
    }
}

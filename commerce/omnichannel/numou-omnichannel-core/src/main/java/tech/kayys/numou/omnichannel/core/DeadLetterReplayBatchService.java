package tech.kayys.numou.omnichannel.core;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Sequential, bounded replay coordinator. Each item has its own authorization
 * check and durable audit transaction; a batch is intentionally not atomic.
 */
public final class DeadLetterReplayBatchService {
    public static final int MAX_BATCH_SIZE = 100;

    private final DeadLetterReplayService replayService;

    public DeadLetterReplayBatchService(
            DeadLetterOperations operations,
            DeadLetterReplayAuthorizer authorizer,
            Clock clock) {
        this.replayService = new DeadLetterReplayService(operations, authorizer, clock);
    }

    public List<DeadLetterReplayBatchItem> replay(
            List<InboundEventKey> keys, String actorId, String reason) {
        Objects.requireNonNull(keys, "keys must not be null");
        if (keys.isEmpty() || keys.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("batch size must be between 1 and " + MAX_BATCH_SIZE);
        }
        if (keys.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("keys must not contain null");
        }
        if (new HashSet<>(keys).size() != keys.size()) {
            throw new IllegalArgumentException("batch must not contain duplicate event keys");
        }

        List<DeadLetterReplayBatchItem> outcomes = new ArrayList<>(keys.size());
        for (InboundEventKey key : keys) {
            try {
                DeadLetterReplayResult result = replayService.replay(key, actorId, reason);
                outcomes.add(new DeadLetterReplayBatchItem(key,
                        result == DeadLetterReplayResult.REQUEUED
                                ? DeadLetterReplayBatchItem.Outcome.REQUEUED
                                : DeadLetterReplayBatchItem.Outcome.NOT_FOUND_OR_NOT_DEAD_LETTER));
            } catch (SecurityException forbidden) {
                // Keep the batch moving; do not disclose whether the row exists.
                outcomes.add(new DeadLetterReplayBatchItem(key, DeadLetterReplayBatchItem.Outcome.FORBIDDEN));
            }
        }
        return List.copyOf(outcomes);
    }
}

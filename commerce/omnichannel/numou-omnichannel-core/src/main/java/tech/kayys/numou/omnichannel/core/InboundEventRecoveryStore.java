package tech.kayys.numou.omnichannel.core;
import java.time.Instant;
/** Bounded, concurrent-safe recovery for ingestion records that cannot be dispatched safely. */
public interface InboundEventRecoveryStore {
    /**
     * Moves stale RECEIVED records with no verified payload reference to DEAD_LETTER.
     * They must not be dispatched; operator tooling should arrange provider redelivery.
     * @return number of records quarantined for operator inspection
     */
    int quarantineStaleReceived(Instant cutoff, Instant now, int limit);
}

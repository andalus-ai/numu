package tech.kayys.numou.history.graph.application;

import tech.kayys.numou.history.graph.spi.HistoricalProjectionOutboxStore.ClaimedProjectionEvent;
import java.util.UUID;

/** Projection writer that can isolate rebuild writes from the active generation. */
public interface GenerationTargetedHistoricalRelationshipProjectionPort
        extends HistoricalRelationshipProjectionPort {
    void projectIntoGeneration(ClaimedProjectionEvent event, UUID generationId) throws Exception;
}

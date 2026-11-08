package tech.kayys.numou.history.graph.application;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionOutboxStore.ClaimedProjectionEvent;
/** Provider adapter must be idempotent by event key and reject stale aggregate revisions. */
@FunctionalInterface public interface HistoricalRelationshipProjectionPort { void project(ClaimedProjectionEvent event) throws Exception; }
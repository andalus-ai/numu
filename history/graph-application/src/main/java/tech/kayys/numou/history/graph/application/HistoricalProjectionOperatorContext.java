package tech.kayys.numou.history.graph.application;

import java.util.UUID;

/** Supplies the authenticated human or service principal responsible for maintenance. */
@FunctionalInterface
public interface HistoricalProjectionOperatorContext {
    UUID requireActorId();
}

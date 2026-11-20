package tech.kayys.numou.history.graph.application;

import java.util.UUID;

/** Supplies the tenant selected by authenticated server-side security context. */
@FunctionalInterface
public interface HistoricalRelationshipTenantContext {
    UUID requireTenantId();
}

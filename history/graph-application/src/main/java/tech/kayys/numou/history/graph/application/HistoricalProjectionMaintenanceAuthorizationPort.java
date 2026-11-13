package tech.kayys.numou.history.graph.application;

import java.util.UUID;

/** Fail-closed authorization for operational graph maintenance commands. */
public interface HistoricalProjectionMaintenanceAuthorizationPort {
    void requireCanRebuildTenant(UUID tenantId);
    void requireCanRebuildPublicCorpus();
    void requireCanAuditTenant(UUID tenantId);
    void requireCanAuditPublicCorpus();
}

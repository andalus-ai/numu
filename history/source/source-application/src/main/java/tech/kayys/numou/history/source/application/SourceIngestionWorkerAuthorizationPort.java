package tech.kayys.numou.history.source.application;

import java.util.UUID;

/** Host authorization boundary for trusted ingestion workers. */
public interface SourceIngestionWorkerAuthorizationPort {
    void requireCanClaim(UUID tenantId, String workerRef);

    void requireCanTransition(UUID tenantId, UUID ingestionId, String workerRef);
}

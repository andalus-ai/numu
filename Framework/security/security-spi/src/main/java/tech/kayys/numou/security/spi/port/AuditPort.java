package tech.kayys.syirkah.security.spi.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.security.domain.audit.AuditEntry;

import java.util.List;

/**
 * Append-only business audit sink (base01.md §P1-18).
 *
 * <p>No update or delete operations exist on this port by design.
 */
public interface AuditPort {

    Uni<Void> append(AuditEntry entry);

    Uni<Void> appendAll(List<AuditEntry> entries);

    /** Retrieves the audit history of one resource, oldest first. */
    Uni<List<AuditEntry>> findByResource(String resource, String resourceId);
}

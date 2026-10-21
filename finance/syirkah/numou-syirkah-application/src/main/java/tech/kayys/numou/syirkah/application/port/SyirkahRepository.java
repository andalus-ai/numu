package tech.kayys.numou.numouapplication.port;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.numoudomain.Syirkah;
import tech.kayys.numou.numoudomain.SyirkahId;

import java.util.Optional;

/**
 * Persistence port. Implementations must enforce tenant isolation and
 * optimistic locking.
 */
public interface SyirkahRepository {

    Optional<Syirkah> find(TenantId tenant, SyirkahId id);

    /**
     * Persists the aggregate. {@code expectedVersion} is the version read by this
     * unit of work (-1 for a
     * new aggregate); a mismatch must raise
     * {@link ConcurrentModificationException}.
     */
    void save(Syirkah syirkah, int expectedVersion);

    class ConcurrentModificationException extends RuntimeException {
        public ConcurrentModificationException(String message) {
            super(message);
        }
    }
}

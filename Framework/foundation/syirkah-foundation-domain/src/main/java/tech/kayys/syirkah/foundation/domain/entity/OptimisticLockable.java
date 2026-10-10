package tech.kayys.syirkah.foundation.domain.entity;

/**
 * Contract for aggregate roots that participate in optimistic concurrency control
 * via a stored-version counter.
 *
 * <p>The stored version is the version the aggregate had when it was last loaded from the
 * database. Repositories use it to build {@code UPDATE … WHERE version = storedVersion}
 * statements; a row-count of zero signals a concurrent modification.
 *
 * <p>This interface is intentionally placed in the foundation so that any bounded context
 * can share persistence infrastructure (e.g. {@code PgDocumentRepository}) without
 * coupling to a specific domain base class.
 */
public interface OptimisticLockable {

    /**
     * Returns the version stored in the database at the time this aggregate was loaded.
     * {@code 0} means the aggregate has never been persisted.
     */
    long storedVersion();

    /**
     * Called by repositories after a successful load or save to record the persisted version.
     * Must not be called by application or domain code.
     */
    void markStored(long version);
}

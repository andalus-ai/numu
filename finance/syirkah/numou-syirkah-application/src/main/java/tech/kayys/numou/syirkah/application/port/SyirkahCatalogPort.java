package tech.kayys.numou.numouapplication.port;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.numouapplication.query.SyirkahSummary;
import tech.kayys.numou.numoudomain.SyirkahId;
import tech.kayys.numou.numoudomain.SyirkahStatus;

import java.util.List;
import java.util.Optional;

/**
 * Read-only listing of a tenant's syirkah, keyset-paged by id (ascending,
 * compared as lowercase uuid text).
 */
public interface SyirkahCatalogPort {

    /**
     * @param after exclusive cursor: the last id of the previous page
     * @param limit 1..500
     */
    List<SyirkahSummary> page(TenantId tenant, Optional<SyirkahStatus> status, Optional<SyirkahId> after, int limit);
}

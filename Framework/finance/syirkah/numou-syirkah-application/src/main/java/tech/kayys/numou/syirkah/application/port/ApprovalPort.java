package tech.kayys.numou.syirkah.application.port;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.syirkah.application.governance.PendingApproval;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Storage of maker-checker requests. Implementations must enforce tenant isolation. */
public interface ApprovalPort {

    void save(PendingApproval approval);

    Optional<PendingApproval> find(TenantId tenant, UUID id);

    List<PendingApproval> pending(TenantId tenant);
}

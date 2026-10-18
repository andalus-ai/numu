package tech.kayys.numou.syirkah.adapter.memory;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.syirkah.application.governance.PendingApproval;
import tech.kayys.numou.syirkah.application.port.ApprovalPort;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemoryApprovalPort implements ApprovalPort {

    private final Map<UUID, PendingApproval> store = new LinkedHashMap<>();

    @Override
    public synchronized void save(PendingApproval approval) {
        store.put(approval.id(), approval);
    }

    @Override
    public synchronized Optional<PendingApproval> find(TenantId tenant, UUID id) {
        return Optional.ofNullable(store.get(id)).filter(p -> p.tenant().equals(tenant));
    }

    @Override
    public synchronized List<PendingApproval> pending(TenantId tenant) {
        List<PendingApproval> out = new ArrayList<>();
        for (PendingApproval p : store.values()) {
            if (p.tenant().equals(tenant) && p.status() == PendingApproval.Status.PENDING) {
                out.add(p);
            }
        }
        return out;
    }
}

package tech.kayys.numou.planning.application.port;

import tech.kayys.numou.planning.domain.WorkBreakdownProgressBaseline;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Tenant-scoped persistence port for immutable progress baselines. */
public interface WorkBreakdownProgressBaselineRepository {
    void create(WorkBreakdownProgressBaseline baseline);
    Optional<WorkBreakdownProgressBaseline> findById(UUID tenantId, UUID baselineId);
    List<WorkBreakdownProgressBaseline> findByScope(UUID tenantId, UUID structureId, UUID nodeId);
}

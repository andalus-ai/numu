package tech.kayys.numou.planning.application.port;

import tech.kayys.numou.planning.domain.WorkBreakdownActivityLink;

import java.util.List;
import java.util.UUID;

/** Tenant-explicit persistence port for WBS-to-schedule associations. */
public interface WorkBreakdownActivityLinkRepository {
    /** Creates a link; an activity can map to only one node per structure. */
    void create(WorkBreakdownActivityLink link);

    List<WorkBreakdownActivityLink> findByStructure(UUID tenantId, UUID structureId);

    List<WorkBreakdownActivityLink> findByPlan(UUID tenantId, UUID planId);
}

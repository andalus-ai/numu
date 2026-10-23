package tech.kayys.numou.planning.application;

import tech.kayys.numou.planning.application.port.WorkBreakdownActivityLinkRepository;
import tech.kayys.numou.planning.application.port.WorkBreakdownRepository;
import tech.kayys.numou.planning.domain.*;

import java.util.Objects;
import java.util.UUID;

/**
 * Links an activity in a schedule plan already loaded through the host's
 * authorized application boundary to a node in the tenant's WBS snapshot.
 */
public final class LinkScheduleActivityToWorkBreakdownNode {
    private final WorkBreakdownRepository structures;
    private final WorkBreakdownActivityLinkRepository links;

    public LinkScheduleActivityToWorkBreakdownNode(
            WorkBreakdownRepository structures,
            WorkBreakdownActivityLinkRepository links) {
        this.structures = Objects.requireNonNull(structures, "WBS repository cannot be null");
        this.links = Objects.requireNonNull(links, "WBS link repository cannot be null");
    }

    public WorkBreakdownActivityLink execute(
            UUID tenantId, SchedulePlan authorizedPlan, UUID structureId, UUID nodeId, ActivityId activityId) {
        Objects.requireNonNull(tenantId, "Tenant id cannot be null");
        Objects.requireNonNull(authorizedPlan, "Authorized schedule plan cannot be null");
        Objects.requireNonNull(structureId, "Structure id cannot be null");
        Objects.requireNonNull(nodeId, "Node id cannot be null");
        Objects.requireNonNull(activityId, "Activity id cannot be null");

        ScheduleActivity activity = authorizedPlan.activities().stream()
                .filter(candidate -> candidate.id().equals(activityId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Activity does not belong to the supplied plan"));

        WorkBreakdownStructure structure = structures.findById(tenantId, structureId)
                .orElseThrow(() -> new IllegalArgumentException("WBS structure not found for tenant"));
        structure.node(nodeId);

        WorkBreakdownActivityLink link = new WorkBreakdownActivityLink(
                tenantId, structure.id(), nodeId, authorizedPlan.id(), activity.id());
        links.create(link);
        return link;
    }
}

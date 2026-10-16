package tech.kayys.numou.project.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.project.domain.milestone.ProjectMilestone;
import tech.kayys.numou.project.domain.milestone.ProjectMilestoneId;
import tech.kayys.numou.project.domain.project.ProjectId;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Persistence port for project milestones.
 *
 * Like phases, milestones are independent consistency boundaries that
 * reference their project by identifier.
 */
public interface ProjectMilestoneRepository
        extends Repository<ProjectMilestone, ProjectMilestoneId> {

    CompletionStage<List<ProjectMilestone>> findByProjectId(ProjectId projectId);
}

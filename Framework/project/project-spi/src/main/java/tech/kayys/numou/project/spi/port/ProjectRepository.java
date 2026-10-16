package tech.kayys.numou.project.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.project.domain.project.Project;
import tech.kayys.numou.project.domain.project.ProjectId;
import tech.kayys.numou.project.domain.project.ProjectNumber;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Persistence port for the {@link Project} aggregate root.
 *
 * This simply specialises the Foundation {@link Repository} contract,
 * so the application layer never depends on Hibernate, JPA or any
 * other storage technology. Implementations live in
 * {@code numou-project-adapter} (in-memory today, JPA/Postgres
 * later) without touching the domain.
 */
public interface ProjectRepository
        extends Repository<Project, ProjectId> {

    /**
     * Looks a project up by its human-readable business key.
     *
     * Tenant scoping is deliberately not part of this signature: the
     * tenant belongs to the runtime context (adapter), not to the
     * domain contract.
     */
    CompletionStage<Optional<Project>> findByNumber(ProjectNumber number);
}

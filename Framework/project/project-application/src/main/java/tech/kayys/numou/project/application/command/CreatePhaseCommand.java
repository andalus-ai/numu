package tech.kayys.syirkah.project.application.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.project.domain.phase.PhaseType;
import tech.kayys.syirkah.project.domain.project.ProjectId;

import java.util.Objects;

/**
 * Adds a phase to an existing project.
 *
 * The project id in the payload is not trusted blindly: the handler
 * verifies that the project exists and is still allowed to receive new
 * phases (see project01.md section 14).
 */
public record CreatePhaseCommand(
        ProjectId projectId,
        int sequence,
        String name,
        PhaseType type
) implements Command {

    public CreatePhaseCommand {
        Objects.requireNonNull(projectId, "projectId cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
    }
}

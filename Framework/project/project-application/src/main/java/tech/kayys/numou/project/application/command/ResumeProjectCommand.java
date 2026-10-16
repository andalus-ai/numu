package tech.kayys.syirkah.project.application.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.project.domain.project.ProjectId;

import java.util.Objects;

/** Resumes a project that was on hold. */
public record ResumeProjectCommand(
        ProjectId projectId
) implements Command {

    public ResumeProjectCommand {
        Objects.requireNonNull(projectId, "projectId cannot be null");
    }
}

package tech.kayys.syirkah.project.application.risk.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.project.domain.risk.IssueId;

import java.util.Objects;

public record StartIssueWorkCommand(
        IssueId issueId
) implements Command {

    public StartIssueWorkCommand {
        Objects.requireNonNull(issueId, "issueId cannot be null");
    }
}

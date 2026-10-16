package tech.kayys.numou.project.application.risk.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.project.domain.risk.IssueId;

import java.util.Objects;

public record RejectIssueCommand(
        IssueId issueId
) implements Command {

    public RejectIssueCommand {
        Objects.requireNonNull(issueId, "issueId cannot be null");
    }
}

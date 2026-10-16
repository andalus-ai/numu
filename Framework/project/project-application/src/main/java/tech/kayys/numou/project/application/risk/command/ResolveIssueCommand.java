package tech.kayys.numou.project.application.risk.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.project.domain.risk.IssueId;

import java.util.Objects;

/**
 * Resolving records BOTH the root cause and the resolution — an
 * issue closed without a root cause teaches nobody anything.
 */
public record ResolveIssueCommand(
        IssueId issueId,
        String rootCause,
        String resolution
) implements Command {

    public ResolveIssueCommand {
        Objects.requireNonNull(issueId, "issueId cannot be null");
        Objects.requireNonNull(rootCause, "rootCause cannot be null");
        Objects.requireNonNull(resolution, "resolution cannot be null");
        if (rootCause.isBlank()) {
            throw new IllegalArgumentException("rootCause cannot be blank");
        }
        if (resolution.isBlank()) {
            throw new IllegalArgumentException("resolution cannot be blank");
        }
    }
}

package tech.kayys.numou.construction.application.boq.command;

import tech.kayys.numou.foundation.application.command.Command;

import java.util.Objects;
import java.util.UUID;

/** Actor identity is intentionally absent: handlers obtain it from trusted security context. */
public record SubmitBoqRevisionForReviewCommand(UUID boqId, int revisionNumber) implements Command {
    public SubmitBoqRevisionForReviewCommand {
        Objects.requireNonNull(boqId, "BOQ id cannot be null");
        if (revisionNumber < 1) throw new IllegalArgumentException("Revision number must be >= 1");
    }
}

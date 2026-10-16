package tech.kayys.syirkah.project.domain.risk;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

public record IssueId(UUID value)
        implements DomainId<UUID> {

    public IssueId {
        Objects.requireNonNull(value, "Issue id cannot be null");
    }

    public static IssueId generate() {
        return new IssueId(UUID.randomUUID());
    }

    public static IssueId of(UUID value) {
        return new IssueId(value);
    }
}

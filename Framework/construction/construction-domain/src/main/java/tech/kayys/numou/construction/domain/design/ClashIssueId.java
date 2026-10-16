package tech.kayys.syirkah.construction.domain.design;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;
import java.util.Objects;
import java.util.UUID;

public record ClashIssueId(UUID value) implements DomainId<UUID> {
    public ClashIssueId { Objects.requireNonNull(value); }
    public static ClashIssueId generate() { return new ClashIssueId(UUID.randomUUID()); }
    public static ClashIssueId of(UUID value) { return new ClashIssueId(value); }
}

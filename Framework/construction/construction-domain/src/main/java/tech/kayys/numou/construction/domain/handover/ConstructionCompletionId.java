package tech.kayys.syirkah.construction.domain.handover;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;
import java.util.Objects;
import java.util.UUID;

public record ConstructionCompletionId(UUID value) implements DomainId<UUID> {
    public ConstructionCompletionId { Objects.requireNonNull(value); }
    public static ConstructionCompletionId generate() { return new ConstructionCompletionId(UUID.randomUUID()); }
    public static ConstructionCompletionId of(UUID value) { return new ConstructionCompletionId(value); }
}

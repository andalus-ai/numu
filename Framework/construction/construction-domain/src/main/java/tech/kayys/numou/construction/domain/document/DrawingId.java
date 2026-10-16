package tech.kayys.syirkah.construction.domain.document;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;
import java.util.Objects;
import java.util.UUID;

public record DrawingId(UUID value) implements DomainId<UUID> {
    public DrawingId { Objects.requireNonNull(value); }
    public static DrawingId generate() { return new DrawingId(UUID.randomUUID()); }
    public static DrawingId of(UUID value) { return new DrawingId(value); }
}

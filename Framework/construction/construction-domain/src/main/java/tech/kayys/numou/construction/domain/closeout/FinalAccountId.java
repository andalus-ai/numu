package tech.kayys.syirkah.construction.domain.closeout;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;
import java.util.Objects;
import java.util.UUID;

public record FinalAccountId(UUID value) implements DomainId<UUID> {
    public FinalAccountId { Objects.requireNonNull(value); }
    public static FinalAccountId generate() { return new FinalAccountId(UUID.randomUUID()); }
    public static FinalAccountId of(UUID value) { return new FinalAccountId(value); }
}

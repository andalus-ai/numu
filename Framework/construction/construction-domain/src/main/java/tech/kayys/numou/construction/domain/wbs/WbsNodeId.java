package tech.kayys.syirkah.construction.domain.wbs;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;
import java.util.Objects;
import java.util.UUID;

public record WbsNodeId(UUID value) implements DomainId<UUID> {
    public WbsNodeId {
        Objects.requireNonNull(value, "WBS node id cannot be null");
    }

    public static WbsNodeId generate() {
        return new WbsNodeId(UUID.randomUUID());
    }

    public static WbsNodeId of(UUID value) {
        return new WbsNodeId(value);
    }
}

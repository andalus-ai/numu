package tech.kayys.numou.developer.apicatalog.domain;

import tech.kayys.numou.foundation.domain.identifier.DomainId;
import java.util.Objects;
import java.util.UUID;

public record ApiProductId(UUID value) implements DomainId<UUID> {
    public ApiProductId { Objects.requireNonNull(value, "value cannot be null"); }
    public static ApiProductId newId() { return new ApiProductId(UUID.randomUUID()); }
    public static ApiProductId of(UUID value) { return new ApiProductId(value); }
}
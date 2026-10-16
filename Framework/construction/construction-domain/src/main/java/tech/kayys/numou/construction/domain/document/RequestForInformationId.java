package tech.kayys.syirkah.construction.domain.document;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;
import java.util.Objects;
import java.util.UUID;

public record RequestForInformationId(UUID value) implements DomainId<UUID> {
    public RequestForInformationId { Objects.requireNonNull(value); }
    public static RequestForInformationId generate() { return new RequestForInformationId(UUID.randomUUID()); }
    public static RequestForInformationId of(UUID value) { return new RequestForInformationId(value); }
}

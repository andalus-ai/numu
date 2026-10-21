package tech.kayys.numou.education.madrasah.domain;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.UUID;

public record IbadahLogId(UUID value) implements DomainId<UUID> {
    public static IbadahLogId of(UUID v) { return new IbadahLogId(v); }
    public static IbadahLogId generate() { return new IbadahLogId(UUID.randomUUID()); }
}

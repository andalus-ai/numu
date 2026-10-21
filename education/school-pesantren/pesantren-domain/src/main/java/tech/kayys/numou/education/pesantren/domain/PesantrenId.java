package tech.kayys.numou.education.pesantren.domain;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.UUID;

public record PesantrenId(UUID value) implements DomainId<UUID> {
    public static PesantrenId of(UUID v) { return new PesantrenId(v); }
    public static PesantrenId generate() { return new PesantrenId(UUID.randomUUID()); }
}

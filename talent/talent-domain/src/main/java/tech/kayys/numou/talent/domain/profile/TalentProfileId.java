package tech.kayys.numou.talent.domain.profile;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

/** Strongly typed identifier for a tenant-scoped professional talent profile. */
public record TalentProfileId(UUID value) implements DomainId<UUID> {

    public TalentProfileId {
        Objects.requireNonNull(value, "Talent profile ID must not be null");
    }

    public static TalentProfileId generate() {
        return new TalentProfileId(UUID.randomUUID());
    }

    public static TalentProfileId of(UUID value) {
        return new TalentProfileId(value);
    }
}

package tech.kayys.numou.workforce.domain.socialinsurance;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

public record SocialInsuranceSchemeId(UUID value) implements DomainId<UUID> {
    public SocialInsuranceSchemeId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static SocialInsuranceSchemeId of(UUID value) { return new SocialInsuranceSchemeId(value); }
    public static SocialInsuranceSchemeId of(String value) { return new SocialInsuranceSchemeId(UUID.fromString(value)); }
    public static SocialInsuranceSchemeId generate() { return new SocialInsuranceSchemeId(UUID.randomUUID()); }

    @Override
    public String toString() { return value.toString(); }
}

package tech.kayys.syirkah.hris.domain.identifier;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

/**
 * Onboarding process identifier.
 */
public record OnboardingId(UUID value) implements DomainId<UUID>, Serializable {

    public OnboardingId {
        Objects.requireNonNull(value, "OnboardingId value cannot be null");
    }

    public static OnboardingId of(UUID value) {
        return new OnboardingId(value);
    }

    public static OnboardingId generate() {
        return new OnboardingId(UUID.randomUUID());
    }

    public static OnboardingId fromString(String value) {
        return new OnboardingId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "OnboardingId{" + value + "}";
    }
}

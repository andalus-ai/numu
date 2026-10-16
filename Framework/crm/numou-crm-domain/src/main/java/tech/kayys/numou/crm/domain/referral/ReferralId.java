package tech.kayys.numou.crm.domain.referral;

import tech.kayys.numou.foundation.domain.identifier.DomainId;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Identifier for a {@link Referral}.
 */
public record ReferralId(UUID value) implements DomainId<UUID>, Serializable {
        public ReferralId {
        value = Objects.requireNonNull(value, "value cannot be null");
    }

    public static ReferralId random() {
        return new ReferralId(UUID.randomUUID());
    }

    public static ReferralId of(UUID value) {
        return new ReferralId(value);
    }

    @Override
    public String toString() {
        return value != null ? value.toString() : "";
    }
}
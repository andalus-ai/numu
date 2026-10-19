package tech.kayys.numou.crm.domain.identifier;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

public record CampaignId(UUID value) implements DomainId<UUID>, Serializable {

    public CampaignId {
        Objects.requireNonNull(value, "CampaignId value cannot be null");
    }

    public static CampaignId of(UUID value) {
        return new CampaignId(value);
    }

    public static CampaignId generate() {
        return new CampaignId(UUID.randomUUID());
    }

    public static CampaignId fromString(String value) {
        return new CampaignId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "CampaignId{" + value + "}";
    }
}

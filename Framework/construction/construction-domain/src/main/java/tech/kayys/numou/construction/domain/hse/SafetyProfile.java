package tech.kayys.syirkah.construction.domain.hse;

import tech.kayys.syirkah.construction.domain.hse.event.SafetyProfileCreated;
import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class SafetyProfile extends AbstractAggregateRoot<SafetyProfileId> {
    private final UUID projectId;
    private final UUID siteId;
    private String name;
    private boolean active;

    private SafetyProfile(SafetyProfileId id, UUID projectId, UUID siteId, String name) {
        super(id);
        this.projectId = Objects.requireNonNull(projectId);
        this.siteId = Objects.requireNonNull(siteId);
        this.name = Objects.requireNonNull(name);
        this.active = false;
    }

    public static SafetyProfile create(UUID projectId, UUID siteId, String name) {
        var profile = new SafetyProfile(SafetyProfileId.generate(), projectId, siteId, name);
        profile.raise(new SafetyProfileCreated(UUID.randomUUID(), Instant.now(), profile.id().value(), projectId, siteId));
        return profile;
    }

    public void activate() { this.active = true; }
    public void deactivate() { this.active = false; }

    public UUID projectId() { return projectId; }
    public UUID siteId() { return siteId; }
    public String name() { return name; }
    public boolean isActive() { return active; }
}

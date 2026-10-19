package tech.kayys.numou.crm.domain.territory;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.crm.domain.event.territory.TerritoryActivated;
import tech.kayys.numou.crm.domain.event.territory.TerritoryCreated;
import tech.kayys.numou.crm.domain.event.territory.TerritoryDefinitionChanged;
import tech.kayys.numou.crm.domain.event.territory.TerritoryParentChanged;
import tech.kayys.numou.crm.domain.event.territory.TerritoryRenamed;
import tech.kayys.numou.crm.domain.event.territory.TerritoryRetired;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A territorial area used for segmentation, routing, and reporting.
 */
public final class Territory extends AbstractAggregateRoot<TerritoryId> {

    private final TerritoryId id;
    private String name;
    private String description;
    private TerritoryId parentTerritoryId;
    private boolean active;

    private Territory(TerritoryId id) {
        super(id);
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.active = false;
    }

    public static Territory create(TerritoryId id,
                                   String name,
                                   String description,
                                   TerritoryId parentTerritoryId) {
        Territory territory = new Territory(id);
        territory.name = Objects.requireNonNull(name, "name cannot be null");
        territory.description = Objects.requireNonNullElse(description, "");
        territory.parentTerritoryId = parentTerritoryId;
        territory.active = false;
        territory.raise(TerritoryCreated.of(
                territory.id.value(),
                territory.name,
                territory.description,
                territory.parentTerritoryId == null ? null : territory.parentTerritoryId.value(),
                Instant.now().toEpochMilli()
        ));
        return territory;
    }

    public static Territory restore(TerritoryId id,
                                    String name,
                                    String description,
                                    TerritoryId parentTerritoryId,
                                    boolean active) {
        Territory territory = new Territory(id);
        territory.name = Objects.requireNonNull(name, "name cannot be null");
        territory.description = Objects.requireNonNullElse(description, "");
        territory.parentTerritoryId = parentTerritoryId;
        territory.active = active;
        return territory;
    }

    public TerritoryId id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public TerritoryId parentTerritoryId() {
        return parentTerritoryId;
    }

    public boolean isActive() {
        return active;
    }

    public void rename(String newName) {
        if (!Objects.equals(this.name, newName)) {
            this.name = Objects.requireNonNull(newName, "newName cannot be null");
            touch();
            raise(TerritoryRenamed.of(
                    id.value(),
                    this.name,
                    Instant.now().toEpochMilli()
            ));
        }
    }

    public void setDescription(String newDescription) {
        if (!Objects.equals(this.description, newDescription)) {
            this.description = Objects.requireNonNullElse(newDescription, "");
            touch();
            raise(TerritoryDefinitionChanged.of(
                    id.value(),
                    this.description,
                    Instant.now().toEpochMilli()
            ));
        }
    }

    public void setParent(TerritoryId parentId) {
        if (parentId != null && parentId.equals(this.id)) {
            throw new IllegalStateException("Territory cannot be its own parent");
        }
        if (!Objects.equals(this.parentTerritoryId, parentId)) {
            UUID oldParentId = this.parentTerritoryId == null ? null : this.parentTerritoryId.value();
            this.parentTerritoryId = parentId;
            touch();
            raise(TerritoryParentChanged.of(
                    id.value(),
                    oldParentId,
                    this.parentTerritoryId == null ? null : this.parentTerritoryId.value(),
                    Instant.now().toEpochMilli()
            ));
        }
    }

    public void activate() {
        if (!this.active) {
            this.active = true;
            touch();
            raise(TerritoryActivated.of(
                    id.value(),
                    Instant.now().toEpochMilli()
            ));
        }
    }

    public void retire() {
        if (this.active) {
            this.active = false;
            touch();
            raise(TerritoryRetired.of(
                    id.value(),
                    Instant.now().toEpochMilli()
            ));
        }
    }

    private void touch() {
        setUpdatedAt(Instant.now());
        incrementVersion();
    }
}
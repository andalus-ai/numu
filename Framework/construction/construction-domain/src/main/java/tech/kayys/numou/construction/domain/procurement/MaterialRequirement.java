package tech.kayys.syirkah.construction.domain.procurement;

import tech.kayys.syirkah.construction.domain.procurement.event.MaterialRequirementCreated;
import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class MaterialRequirement extends AbstractAggregateRoot<MaterialRequirementId> {
    private final UUID projectId;
    private final UUID siteId;
    private final List<RequirementItem> items = new ArrayList<>();
    private MaterialRequirementStatus status;

    private MaterialRequirement(MaterialRequirementId id, UUID projectId, UUID siteId) {
        super(id);
        this.projectId = Objects.requireNonNull(projectId, "Project id cannot be null");
        this.siteId = Objects.requireNonNull(siteId, "Site id cannot be null");
        this.status = MaterialRequirementStatus.DRAFT;
    }

    public static MaterialRequirement create(UUID projectId, UUID siteId) {
        var req = new MaterialRequirement(MaterialRequirementId.generate(), projectId, siteId);
        req.raise(new MaterialRequirementCreated(UUID.randomUUID(), Instant.now(), req.id().value(), projectId, siteId));
        return req;
    }

    public void addItem(RequirementItem item) {
        if (status != MaterialRequirementStatus.DRAFT) throw new IllegalStateException("Items can only be added to draft requirements");
        items.add(Objects.requireNonNull(item, "Item cannot be null"));
    }

    public void submit() {
        if (items.isEmpty()) throw new IllegalStateException("Cannot submit an empty material requirement");
        status = MaterialRequirementStatus.SUBMITTED;
    }

    public void approve() {
        status = MaterialRequirementStatus.APPROVED;
    }

    public UUID projectId() { return projectId; }
    public UUID siteId() { return siteId; }
    public List<RequirementItem> items() { return List.copyOf(items); }
    public MaterialRequirementStatus status() { return status; }
}

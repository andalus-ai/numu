package tech.kayys.syirkah.construction.domain.profile;

import tech.kayys.syirkah.construction.domain.profile.event.ConstructionProjectProfileCreated;
import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class ConstructionProjectProfile extends AbstractAggregateRoot<ConstructionProjectProfileId> {
    private final UUID projectId;
    private ConstructionType constructionType;
    private DeliveryMethod deliveryMethod;
    private ConstructionContractType contractType;
    private String description;

    private ConstructionProjectProfile(
            ConstructionProjectProfileId id,
            UUID projectId,
            ConstructionType constructionType,
            DeliveryMethod deliveryMethod,
            ConstructionContractType contractType,
            String description
    ) {
        super(id);
        this.projectId = Objects.requireNonNull(projectId, "Project id cannot be null");
        this.constructionType = Objects.requireNonNull(constructionType, "Construction type cannot be null");
        this.deliveryMethod = Objects.requireNonNull(deliveryMethod, "Delivery method cannot be null");
        this.contractType = Objects.requireNonNull(contractType, "Contract type cannot be null");
        this.description = description;
    }

    public static ConstructionProjectProfile create(
            UUID projectId,
            ConstructionType constructionType,
            DeliveryMethod deliveryMethod,
            ConstructionContractType contractType,
            String description
    ) {
        var profile = new ConstructionProjectProfile(
                ConstructionProjectProfileId.generate(),
                projectId,
                constructionType,
                deliveryMethod,
                contractType,
                description
        );
        profile.raise(new ConstructionProjectProfileCreated(
                UUID.randomUUID(),
                Instant.now(),
                projectId,
                profile.id().value(),
                constructionType,
                deliveryMethod,
                contractType
        ));
        return profile;
    }

    public UUID projectId() { return projectId; }
    public ConstructionType constructionType() { return constructionType; }
    public DeliveryMethod deliveryMethod() { return deliveryMethod; }
    public ConstructionContractType contractType() { return contractType; }
    public String description() { return description; }
}

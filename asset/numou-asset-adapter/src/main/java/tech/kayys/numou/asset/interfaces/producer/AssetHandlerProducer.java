package tech.kayys.numou.asset.interfaces.producer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import tech.kayys.numou.asset.application.command.*;
import tech.kayys.numou.asset.application.query.AssetReadRepository;
import tech.kayys.numou.asset.application.query.GetAssetHandler;
import tech.kayys.numou.asset.application.query.GetAssetMovementsHandler;
import tech.kayys.numou.asset.application.query.SearchAssetsHandler;
import tech.kayys.numou.asset.domain.repository.AssetMovementRepository;
import tech.kayys.numou.asset.domain.repository.AssetMetadataRepository;
import tech.kayys.numou.asset.domain.repository.AssetRepository;
import tech.kayys.numou.asset.domain.repository.AssetRelationshipRepository;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.time.DomainClock;

/**
 * Wires the application-layer handlers as CDI beans so the inbound adapters
 * (REST, SPI) never instantiate application services by hand.
 */
@ApplicationScoped
public class AssetHandlerProducer {

    private final AssetRepository repository;
    private final AssetRelationshipRepository relationshipRepository;
    private final AssetMetadataRepository metadataRepository;
    private final AssetMovementRepository movementRepository;
    private final AssetReadRepository readRepository;
    private final EventPublisher eventPublisher;
    private final UnitOfWork unitOfWork;
    private final DomainClock clock;

    public AssetHandlerProducer(
            AssetRepository repository,
            AssetRelationshipRepository relationshipRepository,
            AssetMetadataRepository metadataRepository,
            AssetMovementRepository movementRepository,
            AssetReadRepository readRepository,
            EventPublisher eventPublisher,
            UnitOfWork unitOfWork,
            DomainClock clock
    ) {
        this.repository = repository;
        this.relationshipRepository = relationshipRepository;
        this.metadataRepository = metadataRepository;
        this.movementRepository = movementRepository;
        this.readRepository = readRepository;
        this.eventPublisher = eventPublisher;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    @Produces
    @Dependent
    public CreateAssetHandler createAssetHandler() {
        return new CreateAssetHandler(repository, eventPublisher, unitOfWork, clock);
    }

    @Produces
    @Dependent
    public ActivateAssetHandler activateAssetHandler() {
        return new ActivateAssetHandler(repository, eventPublisher, unitOfWork, clock);
    }

    @Produces
    @Dependent
    public SuspendAssetHandler suspendAssetHandler() {
        return new SuspendAssetHandler(repository, eventPublisher, unitOfWork, clock);
    }

    @Produces
    @Dependent
    public RetireAssetHandler retireAssetHandler() {
        return new RetireAssetHandler(repository, eventPublisher, unitOfWork, clock);
    }

    @Produces
    @Dependent
    public DisposeAssetHandler disposeAssetHandler() {
        return new DisposeAssetHandler(repository, eventPublisher, unitOfWork, clock);
    }

    @Produces
    @Dependent
    public UpdateAssetHandler updateAssetHandler() {
        return new UpdateAssetHandler(repository, eventPublisher, unitOfWork, clock);
    }

    @Produces
    @Dependent
    public ChangeAssetLocationHandler changeAssetLocationHandler() {
        return new ChangeAssetLocationHandler(repository, eventPublisher, unitOfWork, clock);
    }

    @Produces
    @Dependent
    public ClearAssetLocationHandler clearAssetLocationHandler() {
        return new ClearAssetLocationHandler(repository, eventPublisher, unitOfWork, clock);
    }

    @Produces
    @Dependent
    public AssignAssetHandler assignAssetHandler() {
        return new AssignAssetHandler(repository, eventPublisher, unitOfWork, clock);
    }

    @Produces
    @Dependent
    public UnassignAssetHandler unassignAssetHandler() {
        return new UnassignAssetHandler(repository, eventPublisher, unitOfWork, clock);
    }

    @Produces
    @Dependent
    public ClassifyAssetHandler classifyAssetHandler() {
        return new ClassifyAssetHandler(repository, eventPublisher, unitOfWork, clock);
    }

    @Produces
    @Dependent
    public ClearAssetClassificationHandler clearAssetClassificationHandler() {
        return new ClearAssetClassificationHandler(repository, eventPublisher, unitOfWork, clock);
    }

    @Produces
    @Dependent
    public AddAssetRelationshipHandler addAssetRelationshipHandler() {
        return new AddAssetRelationshipHandler(
                repository, relationshipRepository, eventPublisher, unitOfWork, clock);
    }

    @Produces
    @Dependent
    public RemoveAssetRelationshipHandler removeAssetRelationshipHandler() {
        return new RemoveAssetRelationshipHandler(
                repository, relationshipRepository, eventPublisher, unitOfWork, clock);
    }

    @Produces
    @Dependent
    public GetAssetHandler getAssetHandler() {
        return new GetAssetHandler(readRepository);
    }

    @Produces
    @Dependent
    public SearchAssetsHandler searchAssetsHandler() {
        return new SearchAssetsHandler(readRepository);
    }

    @Produces
    @Dependent
    public GetAssetMovementsHandler getAssetMovementsHandler() {
        return new GetAssetMovementsHandler(movementRepository);
    }

    @Produces
    @Dependent
    public ReplaceAssetMetadataHandler replaceAssetMetadataHandler() {
        return new ReplaceAssetMetadataHandler(repository, metadataRepository);
    }
}

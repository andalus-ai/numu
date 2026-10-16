package tech.kayys.numou.asset.application.finance;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.asset.application.command.AbstractAssetCommandHandler;
import tech.kayys.numou.asset.domain.event.FixedAssetCapitalized;
import tech.kayys.numou.asset.domain.finance.AssetAccountingLink;
import tech.kayys.numou.asset.domain.finance.AssetAccountingLinkId;
import tech.kayys.numou.asset.domain.finance.AssetAccountingReconciliation;
import tech.kayys.numou.asset.domain.finance.AssetFinancialSnapshot;
import tech.kayys.numou.asset.domain.finance.FixedAssetStatus;
import tech.kayys.numou.asset.domain.repository.AssetAccountingLinkRepository;
import tech.kayys.numou.asset.domain.repository.AssetAccountingReconciliationRepository;
import tech.kayys.numou.asset.domain.repository.AssetFinancialSnapshotRepository;
import tech.kayys.numou.asset.domain.repository.AssetRepository;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.time.DomainClock;

import java.util.List;
import java.util.UUID;

/** Applies an Accounting capitalization (ASSET-25 section 16). Idempotent on eventId. */
@jakarta.enterprise.context.ApplicationScoped
public class RecordCapitalizationHandler extends AbstractAssetCommandHandler
        implements CommandHandler<RecordCapitalizationCommand, Result<Void>> {

    private final AssetAccountingLinkRepository links;
    private final AssetFinancialSnapshotRepository snapshots;
    private final AssetAccountingReconciliationRepository reconciliations;

    public RecordCapitalizationHandler(AssetRepository repository, AssetAccountingLinkRepository links,
            AssetFinancialSnapshotRepository snapshots, AssetAccountingReconciliationRepository reconciliations,
            EventPublisher eventPublisher, UnitOfWork unitOfWork, DomainClock clock) {
        super(repository, eventPublisher, unitOfWork, clock);
        this.links = links;
        this.snapshots = snapshots;
        this.reconciliations = reconciliations;
    }

    @Override
    public Uni<Result<Void>> handle(RecordCapitalizationCommand c) {
        return requireAsset(c.tenantId(), c.assetId()).flatMap(asset -> Uni.createFrom()
                .completionStage(() -> snapshots.existsBySourceEvent(c.tenantId(), key(c))).flatMap(seen -> {
                    if (Boolean.TRUE.equals(seen)) {
                        return Uni.createFrom().item(Result.<Void>success(null));
                    }
                    return Uni.createFrom().completionStage(() -> links.findByAsset(c.tenantId(), c.assetId()))
                            .flatMap(existing -> {
                                AssetAccountingLink link = existing
                                        .filter(l -> l.accountingAssetId().equals(c.accountingAssetId()))
                                        .orElseGet(() -> new AssetAccountingLink(AssetAccountingLinkId.generate(),
                                                c.tenantId(), c.assetId(), c.accountingAssetId(),
                                                c.accountingAssetNumber(), clock().now(), null, true));
                                AssetFinancialSnapshot snap = new AssetFinancialSnapshot(c.assetId(),
                                        c.accountingAssetId(), c.acquisitionCost(), null, c.acquisitionCost(),
                                        null, null, c.currency(), c.capitalizationDate(), null, null,
                                        FixedAssetStatus.CAPITALIZED, clock().now());
                                AssetAccountingReconciliation rec = new AssetAccountingReconciliation(
                                        UUID.randomUUID(), c.tenantId(), c.assetId(), c.accountingAssetId(),
                                        AssetAccountingReconciliation.ReconciliationStatus.MATCHED,
                                        "capitalized", clock().now(), null);
                                FixedAssetCapitalized event = new FixedAssetCapitalized(
                                        c.eventId() == null ? UUID.randomUUID() : c.eventId(), clock().now(),
                                        c.accountingAssetId(), c.assetId().value(), c.accountingAssetNumber(),
                                        c.acquisitionCost(), c.currency(), c.capitalizationDate());
                                return unitOfWork.execute(() -> Uni.createFrom()
                                        .completionStage(() -> links.save(link))
                                        .flatMap(s -> Uni.createFrom().completionStage(
                                                () -> snapshots.save(c.tenantId(), snap, key(c), "capitalized")))
                                        .flatMap(s -> Uni.createFrom().completionStage(
                                                () -> reconciliations.save(rec)))
                                        .flatMap(s -> eventPublisher.publish(List.of(event))))
                                        .map(ignored -> Result.<Void>success(null));
                            });
                }));
    }

    private static String key(RecordCapitalizationCommand c) {
        return c.eventId() == null ? "capitalized:" + c.accountingAssetId() : c.eventId().toString();
    }
}

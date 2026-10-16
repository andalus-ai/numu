package tech.kayys.numou.asset.application.finance;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.asset.application.command.AbstractAssetCommandHandler;
import tech.kayys.numou.asset.domain.event.FixedAssetDisposed;
import tech.kayys.numou.asset.domain.finance.AssetAccountingReconciliation;
import tech.kayys.numou.asset.domain.finance.AssetFinancialSnapshot;
import tech.kayys.numou.asset.domain.finance.FixedAssetStatus;
import tech.kayys.numou.asset.domain.repository.AssetAccountingLinkRepository;
import tech.kayys.numou.asset.domain.repository.AssetAccountingReconciliationRepository;
import tech.kayys.numou.asset.domain.repository.AssetFinancialSnapshotRepository;
import tech.kayys.numou.asset.domain.repository.AssetRepository;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.ApplicationErrorException;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.time.DomainClock;
import java.util.List;
import java.util.UUID;

@jakarta.enterprise.context.ApplicationScoped
public class RecordDisposalHandler extends AbstractAssetCommandHandler
        implements CommandHandler<RecordDisposalCommand, Result<Void>> {
    private final AssetAccountingLinkRepository links;
    private final AssetFinancialSnapshotRepository snapshots;
    private final AssetAccountingReconciliationRepository recs;

    public RecordDisposalHandler(AssetRepository r, AssetAccountingLinkRepository l,
            AssetFinancialSnapshotRepository s, AssetAccountingReconciliationRepository e,
            EventPublisher p, UnitOfWork u, DomainClock c) {
        super(r, p, u, c);
        this.links = l;
        this.snapshots = s;
        this.recs = e;
    }

    @Override
    public Uni<Result<Void>> handle(RecordDisposalCommand c) {
        String key = c.eventId() == null ? "disposed:" + c.accountingAssetId() : c.eventId().toString();
        return requireAsset(c.tenantId(), c.assetId()).flatMap(a -> Uni.createFrom()
                .completionStage(() -> snapshots.existsBySourceEvent(c.tenantId(), key)).flatMap(seen -> {
                    if (Boolean.TRUE.equals(seen)) return Uni.createFrom().item(Result.<Void>success(null));
                    return Uni.createFrom().completionStage(() -> links.findByAsset(c.tenantId(), c.assetId()))
                    .flatMap(link -> {
                        if (link.isEmpty() || !link.get().accountingAssetId().equals(c.accountingAssetId()))
                            return Uni.createFrom().failure(new ApplicationErrorException(ApplicationError.of(
                                    "asset.accounting.missing-link", "No accounting link for asset")));
                        return Uni.createFrom().completionStage(() -> snapshots.findByAsset(c.tenantId(), c.assetId()))
                        .flatMap(prior -> {
                            var prev = prior.orElse(null);
                            var snap = new AssetFinancialSnapshot(c.assetId(), c.accountingAssetId(),
                                    prev == null ? null : prev.acquisitionCost(),
                                    prev == null ? null : prev.accumulatedDepreciation(),
                                    prev == null ? null : prev.netBookValue(), null, c.proceeds(), c.currency(),
                                    prev == null ? null : prev.capitalizationDate(),
                                    prev == null ? null : prev.lastDepreciationDate(), clock().now(),
                                    FixedAssetStatus.DISPOSED, clock().now());
                            var rec = new AssetAccountingReconciliation(UUID.randomUUID(), c.tenantId(),
                                    c.assetId(), c.accountingAssetId(),
                                    AssetAccountingReconciliation.ReconciliationStatus.MATCHED,
                                    "disposed", clock().now(), null);
                            var event = new FixedAssetDisposed(c.eventId() == null ? UUID.randomUUID() : c.eventId(),
                                    clock().now(), c.accountingAssetId(), c.assetId().value(), c.proceeds(), c.currency());
                            return unitOfWork.execute(() -> Uni.createFrom()
                                    .completionStage(() -> snapshots.save(c.tenantId(), snap, key, "disposed"))
                                    .flatMap(s -> Uni.createFrom().completionStage(() -> recs.save(rec)))
                                    .flatMap(s -> eventPublisher.publish(List.of(event))))
                                    .map(ignored -> Result.<Void>success(null));
                        });
                    });
                }));
    }
}

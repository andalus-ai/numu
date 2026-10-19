package tech.kayys.numou.asset.application.finance;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.asset.application.command.AbstractAssetCommandHandler;
import tech.kayys.numou.asset.domain.event.AssetAccountingLinked;
import tech.kayys.numou.asset.domain.finance.AssetAccountingLink;
import tech.kayys.numou.asset.domain.finance.AssetAccountingLinkId;
import tech.kayys.numou.asset.domain.repository.AssetAccountingLinkRepository;
import tech.kayys.numou.asset.domain.repository.AssetRepository;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.time.DomainClock;

import java.util.List;
import java.util.UUID;

/**
 * Links an operational asset to its Accounting fixed asset (ASSET-25).
 *
 * <p>One active link per asset: relinking the same asset replaces the link
 * (old row deactivated) and publishes {@code asset.accounting-linked} via the
 * outbox-backed {@link UnitOfWork}. A different asset already bound to the
 * same accounting asset is rejected (backed by the DB unique index).</p>
 */
@jakarta.enterprise.context.ApplicationScoped
public class LinkAssetToAccountingHandler extends AbstractAssetCommandHandler
        implements CommandHandler<LinkAssetToAccountingCommand, Result<UUID>> {

    private final AssetAccountingLinkRepository links;

    public LinkAssetToAccountingHandler(AssetRepository repository,
                                        AssetAccountingLinkRepository links,
                                        EventPublisher eventPublisher,
                                        UnitOfWork unitOfWork,
                                        DomainClock clock) {
        super(repository, eventPublisher, unitOfWork, clock);
        this.links = links;
    }

    @Override
    public Uni<Result<UUID>> handle(LinkAssetToAccountingCommand command) {
        return requireAsset(command.tenantId(), command.assetId())
                .flatMap(asset -> Uni.createFrom()
                        .completionStage(() -> links.findByAccountingAsset(
                                command.tenantId(), command.accountingAssetId()))
                        .flatMap(byAccounting -> {
                            if (byAccounting.isPresent()
                                    && !byAccounting.get().assetId().equals(command.assetId())) {
                                return Uni.createFrom().item(Result.<UUID>failure(ApplicationError.of(
                                        "asset.accounting.duplicate",
                                        "Accounting asset already linked to another asset")));
                            }
                            return Uni.createFrom()
                                    .completionStage(() -> links.findByAsset(
                                            command.tenantId(), command.assetId()))
                                    .flatMap(existing -> {
                                        AssetAccountingLinkId id = existing.map(AssetAccountingLink::id)
                                                .orElseGet(AssetAccountingLinkId::generate);
                                        AssetAccountingLink link = new AssetAccountingLink(
                                                id, command.tenantId(), command.assetId(),
                                                command.accountingAssetId(), command.accountingAssetNumber(),
                                                clock().now(), command.linkedBy(), true);
                                        Uni<AssetAccountingLink> deactivateOld =
                                                existing.filter(old -> !old.accountingAssetId()
                                                                .equals(command.accountingAssetId()))
                                                        .map(old -> Uni.createFrom().completionStage(() -> links.save(
                                                                new AssetAccountingLink(old.id(), old.tenantId(),
                                                                        old.assetId(), old.accountingAssetId(),
                                                                        old.accountingAssetNumber(), old.linkedAt(),
                                                                        old.linkedBy(), false)))
                                                        .replaceWith(link))
                                                        .orElseGet(() -> Uni.createFrom().item(link));
                                        return unitOfWork.execute(() -> deactivateOld.flatMap(target -> Uni.createFrom()
                                                        .completionStage(() -> links.save(target))
                                                        .flatMap(saved -> eventPublisher.publish(List.of(
                                                                new AssetAccountingLinked(UUID.randomUUID(),
                                                                        clock().now(), saved.assetId().value(),
                                                                        saved.accountingAssetId())))
                                                                .replaceWith(saved))))
                                                .map(saved -> Result.success(saved.id().value()));
                                    });
                        }));
    }
}

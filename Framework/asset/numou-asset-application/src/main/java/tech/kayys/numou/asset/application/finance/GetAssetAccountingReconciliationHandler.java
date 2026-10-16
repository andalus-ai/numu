package tech.kayys.numou.asset.application.finance;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.asset.domain.finance.AssetAccountingLink;
import tech.kayys.numou.asset.domain.finance.AssetAccountingReconciliation;
import tech.kayys.numou.asset.domain.finance.AssetFinancialSnapshot;
import tech.kayys.numou.asset.domain.identifier.AssetId;
import tech.kayys.numou.asset.domain.repository.AssetAccountingLinkRepository;
import tech.kayys.numou.asset.domain.repository.AssetAccountingReconciliationRepository;
import tech.kayys.numou.asset.domain.repository.AssetFinancialSnapshotRepository;
import java.util.List;
import java.util.Objects;

/** Reconciliation query: list links + snapshots + entries for a tenant (ASSET-25 section 34). */
@jakarta.enterprise.context.ApplicationScoped
public class GetAssetAccountingReconciliationHandler {
    private final AssetAccountingLinkRepository links;
    private final AssetFinancialSnapshotRepository snapshots;
    private final AssetAccountingReconciliationRepository recs;

    public GetAssetAccountingReconciliationHandler(AssetAccountingLinkRepository l,
            AssetFinancialSnapshotRepository s, AssetAccountingReconciliationRepository e) {
        this.links = Objects.requireNonNull(l);
        this.snapshots = Objects.requireNonNull(s);
        this.recs = Objects.requireNonNull(e);
    }

    public Uni<ReconciliationView> handle(String tenantId) {
        Uni<List<AssetAccountingLink>> allLinks = Uni.createFrom().completionStage(() -> links.findAll(tenantId));
        Uni<List<AssetAccountingReconciliation>> allRecs = Uni.createFrom().completionStage(() -> recs.findAll(tenantId));
        return Uni.combine().all().unis(allLinks, allRecs).with((l, e) ->
                new ReconciliationView(tenantId, l, e));
    }

    public Uni<AssetFinancialSnapshot> financialSummary(String tenantId, AssetId assetId) {
        return Uni.createFrom().completionStage(() -> snapshots.findByAsset(tenantId, assetId))
                .map(o -> o.orElse(null));
    }

    public record ReconciliationView(String tenantId, List<AssetAccountingLink> links,
            List<AssetAccountingReconciliation> entries) {
    }
}

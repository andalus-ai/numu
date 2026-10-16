package tech.kayys.numou.asset.infrastructure.persistence.inspection;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.numou.asset.application.inspection.AssetConditionView;
import tech.kayys.numou.asset.application.inspection.AssetInspectionPage;
import tech.kayys.numou.asset.application.inspection.AssetInspectionReadModel;
import tech.kayys.numou.asset.application.inspection.AssetInspectionReadRepository;
import tech.kayys.numou.asset.application.inspection.AssetInspectionSearchCriteria;
import tech.kayys.numou.asset.domain.inspection.AssetInspection;
import tech.kayys.numou.asset.domain.inspection.AssetInspectionStatus;
import tech.kayys.numou.asset.domain.inspection.AssetInspectionId;
import tech.kayys.numou.asset.domain.inspection.InspectionItem;
import tech.kayys.numou.asset.domain.inspection.InspectionItemId;
import tech.kayys.numou.asset.domain.inspection.finding.InspectionFinding;
import tech.kayys.numou.asset.domain.inspection.finding.InspectionFindingId;
import tech.kayys.numou.asset.domain.repository.AssetInspectionRepository;
import tech.kayys.numou.asset.domain.repository.InspectionFindingRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/** Panache adapter for inspections + findings (ASSET-20). Explicit sessions/transactions. */
@ApplicationScoped
public class AssetInspectionRepositoryAdapter
        implements AssetInspectionRepository, InspectionFindingRepository, AssetInspectionReadRepository {

    @Override
    public CompletionStage<Optional<AssetInspection>> findById(String tenantId, AssetInspectionId id) {
        return Panache.withSession(() -> AssetInspectionEntity
                        .<AssetInspectionEntity>find("tenantId = ?1 and id = ?2", tenantId, id.value())
                        .firstResult()
                        .flatMap(entity -> entity == null
                                ? Uni.createFrom().item(Optional.<AssetInspection>empty())
                                : loadChildren(entity).map(c -> Optional.of(toDomain(entity, c)))))
                .subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<AssetInspection> save(String tenantId, AssetInspection inspection) {
        AssetInspectionEntity entity = toEntity(inspection);
        List<AssetInspectionItemEntity> items = inspection.items().stream()
                .map(item -> toItemEntity(inspection, item)).collect(Collectors.toList());
        List<AssetInspectionFindingEntity> findings = inspection.findings().stream()
                .map(AssetInspectionRepositoryAdapter::toFindingEntity).collect(Collectors.toList());
        return Panache.withTransaction(() -> Panache.getSession()
                        .flatMap(session -> session.<AssetInspectionEntity>merge(entity)
                                .flatMap(m -> {
                                    Uni<Void> done = Uni.createFrom().nullItem();
                                    for (AssetInspectionItemEntity it : items) {
                                        done = done.flatMap(v -> session
                                                .<AssetInspectionItemEntity>merge(it)
                                                .replaceWith((Void) null));
                                    }
                                    for (AssetInspectionFindingEntity f : findings) {
                                        done = done.flatMap(v -> session
                                                .<AssetInspectionFindingEntity>merge(f)
                                                .replaceWith((Void) null));
                                    }
                                    return done.replaceWith(inspection);
                                })))
                .subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<List<AssetInspection>> findByAssetId(String tenantId, UUID assetId) {
        return Panache.withSession(() -> AssetInspectionEntity
                        .<AssetInspectionEntity>list(
                                "tenantId = ?1 and assetId = ?2 order by createdAt desc, id desc",
                                tenantId, assetId)
                        .flatMap(entities -> {
                            Uni<List<AssetInspection>> acc = Uni.createFrom().item(new ArrayList<>());
                            for (AssetInspectionEntity entity : entities) {
                                acc = acc.flatMap(list -> loadChildren(entity).map(c -> {
                                    list.add(toDomain(entity, c));
                                    return list;
                                }));
                            }
                            return acc;
                        }))
                .subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<InspectionFinding> save(String tenantId, InspectionFinding finding) {
        AssetInspectionFindingEntity entity = toFindingEntity(finding);
        return Panache.withTransaction(() -> Panache.getSession()
                        .flatMap(session -> session.<AssetInspectionFindingEntity>merge(entity))
                        .replaceWith(finding))
                .subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<List<InspectionFinding>> findByInspection(
            String tenantId, AssetInspectionId inspectionId) {
        return Panache.withSession(() -> AssetInspectionFindingEntity
                        .<AssetInspectionFindingEntity>list(
                                "tenantId = ?1 and inspectionId = ?2 order by recordedAt asc, id asc",
                                tenantId, inspectionId.value())
                        .map(entities -> entities.stream()
                                .map(AssetInspectionRepositoryAdapter::toFindingDomain)
                                .collect(Collectors.toList())))
                .subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<AssetInspectionPage<AssetInspectionReadModel>> search(
            AssetInspectionSearchCriteria criteria) {
        StringBuilder where = new StringBuilder("tenantId = :tenant");
        Map<String, Object> params = new HashMap<>();
        params.put("tenant", criteria.tenantId());
        if (criteria.assetId() != null) {
            where.append(" and assetId = :assetId");
            params.put("assetId", criteria.assetId());
        }
        if (criteria.type() != null) {
            where.append(" and inspectionType = :type");
            params.put("type", criteria.type());
        }
        if (criteria.status() != null) {
            where.append(" and status = :status");
            params.put("status", criteria.status());
        }
        if (criteria.condition() != null) {
            where.append(" and overallCondition = :condition");
            params.put("condition", criteria.condition());
        }
        if (criteria.result() != null) {
            where.append(" and result = :result");
            params.put("result", criteria.result());
        }
        String whereClause = where.toString();
        String ordered = whereClause + " order by createdAt desc, id desc";

        return Panache.withSession(() -> AssetInspectionEntity.count(whereClause, params)
                        .flatMap(total -> total == 0
                                ? Uni.createFrom().item(AssetInspectionPage
                                        .<AssetInspectionReadModel>of(List.of(),
                                                criteria.page(), criteria.size(), 0L))
                                : AssetInspectionEntity.<AssetInspectionEntity>find(ordered, params)
                                        .page(criteria.page(), criteria.size())
                                        .list()
                                        .map(rows -> AssetInspectionPage.of(
                                                rows.stream()
                                                        .map(AssetInspectionRepositoryAdapter::toReadModel)
                                                        .collect(Collectors.toList()),
                                                criteria.page(), criteria.size(), total))))
                .subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<Optional<AssetInspectionReadModel>> findById(
            String tenantId, UUID inspectionId) {
        return Panache.withSession(() -> AssetInspectionEntity
                        .<AssetInspectionEntity>find("tenantId = ?1 and id = ?2",
                                tenantId, inspectionId)
                        .firstResult()
                        .map(entity -> entity == null
                                ? Optional.<AssetInspectionReadModel>empty()
                                : Optional.of(toReadModel(entity))))
                .subscribe().asCompletionStage();
    }

    /** ASSET-20 §20.24: latest COMPLETED inspection only; cancelled never counts. */
    @Override
    public CompletionStage<Optional<AssetConditionView>> findCurrentCondition(
            String tenantId, UUID assetId) {
        return Panache.withSession(() -> AssetInspectionEntity
                        .<AssetInspectionEntity>find(
                                "tenantId = ?1 and assetId = ?2 and status = ?3 "
                                        + "and overallCondition is not null "
                                        + "order by completedAt desc, id desc",
                                tenantId, assetId, AssetInspectionStatus.COMPLETED)
                        .firstResult()
                        .map(entity -> entity == null
                                ? Optional.<AssetConditionView>empty()
                                : Optional.of(new AssetConditionView(
                                        entity.assetId, entity.overallCondition,
                                        entity.id, entity.completedAt))))
                .subscribe().asCompletionStage();
    }
    // ── Mapping ─────────────────────────────────────────────────────────────

    private record Children(
            List<AssetInspectionItemEntity> items,
            List<AssetInspectionFindingEntity> findings
    ) {
    }

    private static Uni<Children> loadChildren(AssetInspectionEntity entity) {
        return AssetInspectionItemEntity.<AssetInspectionItemEntity>list(
                        "inspectionId = ?1", entity.id)
                .flatMap(items -> AssetInspectionFindingEntity
                        .<AssetInspectionFindingEntity>list("inspectionId = ?1", entity.id)
                        .map(findings -> new Children(items, findings)));
    }

    private static AssetInspection toDomain(AssetInspectionEntity entity, Children children) {
        List<InspectionItem> items = children.items().stream()
                .map(AssetInspectionRepositoryAdapter::toItemDomain)
                .collect(Collectors.toList());
        List<InspectionFinding> findings = children.findings().stream()
                .map(AssetInspectionRepositoryAdapter::toFindingDomain)
                .collect(Collectors.toList());

        AssetInspection inspection = AssetInspection.reconstitute(
                AssetInspectionId.of(entity.id), entity.tenantId, entity.assetId,
                entity.inspectionNumber, entity.inspectionType, entity.status,
                entity.overallCondition, entity.result, entity.inspectorId, entity.notes,
                entity.workOrderId, entity.scheduledFor, entity.startedAt,
                entity.completedAt, entity.cancelledAt, items, findings);
        if (entity.createdAt != null) {
            inspection.setCreatedAt(entity.createdAt);
        }
        if (entity.updatedAt != null) {
            inspection.setUpdatedAt(entity.updatedAt);
        }
        if (entity.version != null) {
            inspection.setVersion(entity.version.intValue());
        }
        return inspection;
    }

    private static AssetInspectionEntity toEntity(AssetInspection inspection) {
        AssetInspectionEntity entity = new AssetInspectionEntity();
        entity.id = inspection.id().value();
        entity.tenantId = inspection.tenantId();
        entity.assetId = inspection.assetId();
        entity.inspectionNumber = inspection.inspectionNumber();
        entity.inspectionType = inspection.type();
        entity.status = inspection.status();
        entity.overallCondition = inspection.overallCondition();
        entity.result = inspection.result();
        entity.inspectorId = inspection.inspectorId();
        entity.notes = inspection.notes();
        entity.workOrderId = inspection.workOrderId();
        entity.scheduledFor = inspection.scheduledFor();
        entity.startedAt = inspection.startedAt();
        entity.completedAt = inspection.completedAt();
        entity.cancelledAt = inspection.cancelledAt();
        entity.createdAt = inspection.getCreatedAt();
        entity.updatedAt = inspection.getUpdatedAt();
        return entity;
    }

    private static AssetInspectionItemEntity toItemEntity(
            AssetInspection inspection, InspectionItem item) {
        AssetInspectionItemEntity entity = new AssetInspectionItemEntity();
        entity.id = item.id().value();
        entity.tenantId = inspection.tenantId();
        entity.inspectionId = inspection.id().value();
        entity.component = item.component();
        entity.description = item.description();
        entity.condition = item.condition();
        entity.result = item.result();
        entity.notes = item.notes();
        return entity;
    }

    private static InspectionItem toItemDomain(AssetInspectionItemEntity entity) {
        return new InspectionItem(
                InspectionItemId.of(entity.id), entity.component, entity.description,
                entity.condition, entity.result, entity.notes);
    }

    private static AssetInspectionFindingEntity toFindingEntity(InspectionFinding finding) {
        AssetInspectionFindingEntity entity = new AssetInspectionFindingEntity();
        entity.id = finding.id().value();
        entity.tenantId = finding.tenantId();
        entity.inspectionId = finding.inspectionId().value();
        entity.assetId = finding.assetId();
        entity.category = finding.category();
        entity.description = finding.description();
        entity.severity = finding.severity();
        entity.recommendedAction = finding.recommendedAction();
        entity.workOrderId = finding.workOrderId();
        entity.recordedAt = finding.recordedAt();
        entity.recordedBy = finding.recordedBy();
        return entity;
    }

    private static InspectionFinding toFindingDomain(AssetInspectionFindingEntity entity) {
        return new InspectionFinding(
                InspectionFindingId.of(entity.id),
                AssetInspectionId.of(entity.inspectionId),
                entity.tenantId, entity.assetId, entity.category, entity.description,
                entity.severity, entity.recommendedAction, entity.workOrderId,
                entity.recordedAt, entity.recordedBy);
    }

    private static AssetInspectionReadModel toReadModel(AssetInspectionEntity entity) {
        return new AssetInspectionReadModel(
                entity.id, entity.tenantId, entity.assetId, entity.inspectionNumber,
                entity.inspectionType, entity.status, entity.overallCondition,
                entity.result, entity.inspectorId, entity.notes, entity.workOrderId,
                entity.scheduledFor, entity.startedAt, entity.completedAt,
                entity.cancelledAt);
    }
}

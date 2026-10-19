package com.saas.product.infrastructure;

import com.saas.product.core.ProductAggregate;
import com.saas.product.core.lifecycle.ProductStatus;
import com.saas.product.core.model.ProductCore;
import com.saas.product.core.model.ProductId;
import com.saas.product.core.model.ProductType;
import com.saas.product.infrastructure.entity.ProductJpaEntity;
import com.saas.product.spi.ProductExtension;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Map;

/**
 * Bidirectional mapper: ProductAggregate ↔ ProductJpaEntity.
 *
 * Kept in the infrastructure layer — the domain aggregate has zero
 * knowledge of JPA.
 */
@ApplicationScoped
public class ProductAggregateMapper {

    @Inject
    ExtensionSerializer extensionSerializer;

    // ── Aggregate → Entity ────────────────────────────────────────────────

    public ProductJpaEntity toEntity(ProductAggregate agg) {
        ProductJpaEntity e = new ProductJpaEntity();
        e.id          = agg.getId().getValue();
        e.tenantId    = agg.getTenantId();
        e.sku         = agg.getCore().getSku();
        e.name        = agg.getCore().getName();
        e.description = agg.getCore().getDescription();
        e.type        = agg.getCore().getType().name();
        e.categoryId  = agg.getCore().getCategoryId();
        e.brandId     = agg.getCore().getBrandId();
        e.externalRef = agg.getCore().getExternalRef();
        e.status      = agg.getStatus().name();
        e.attributes  = agg.getCore().getAttributes();
        e.labels      = agg.getCore().getLabels();
        e.extensions  = extensionSerializer.serialize(agg.getExtensions());
        e.version     = agg.getVersion();
        e.createdAt   = agg.getCreatedAt();
        e.updatedAt   = agg.getUpdatedAt();
        e.createdBy   = agg.getCreatedBy();
        e.updatedBy   = agg.getUpdatedBy();
        return e;
    }

    // ── Entity → Aggregate ────────────────────────────────────────────────

    public ProductAggregate toDomain(ProductJpaEntity e) {
        ProductCore core = ProductCore.builder()
                .id(ProductId.of(e.id))
                .tenantId(e.tenantId)
                .sku(e.sku)
                .name(e.name)
                .description(e.description)
                .type(ProductType.valueOf(e.type))
                .categoryId(e.categoryId)
                .brandId(e.brandId)
                .externalRef(e.externalRef)
                .attributes(e.attributes != null ? e.attributes : Map.of())
                .labels(e.labels != null ? e.labels : Map.of())
                .build();

        Map<String, ProductExtension> extensions =
                extensionSerializer.deserialize(e.extensions);

        return ProductAggregate.reconstitute(
                core,
                extensions,
                ProductStatus.valueOf(e.status),
                e.version,
                e.createdAt,
                e.updatedAt,
                e.createdBy,
                e.updatedBy);
    }
}

package com.saas.product.infrastructure.jpa;

import com.saas.product.core.ProductAggregate;
import com.saas.product.core.lifecycle.ProductStatus;
import com.saas.product.core.model.ProductId;
import com.saas.product.infrastructure.ProductAggregateMapper;
import com.saas.product.infrastructure.entity.ProductJpaEntity;
import com.saas.product.spi.ProductQuery;
import com.saas.product.spi.ProductRepository;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.OptimisticLockException;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Quarkus Panache implementation of both ProductRepository (write) and
 * ProductQuery (read) backed by PostgreSQL.
 *
 * Optimistic locking:
 *  The @Version field on ProductJpaEntity prevents lost updates.
 *  The save() method merges using persist() which Panache handles correctly.
 *
 * Full-text search:
 *  Uses PostgreSQL to_tsvector / to_tsquery via native query.
 *  Requires GIN index on products(name, description) — see migration V2.
 */
@ApplicationScoped
public class PanacheProductRepository
        implements ProductRepository, ProductQuery,
                   PanacheRepositoryBase<ProductJpaEntity, String> {

    @Inject
    ProductAggregateMapper mapper;

    // ── ProductRepository (write) ─────────────────────────────────────────

    @Override
    @Transactional
    public ProductAggregate save(ProductAggregate product) {
        ProductJpaEntity entity = mapper.toEntity(product);
        try {
            // Panache: persist or merge based on managed state
            if (findByIdOptional(entity.id).isPresent()) {
                getEntityManager().merge(entity);
            } else {
                persist(entity);
            }
        } catch (OptimisticLockException e) {
            throw new ConcurrentProductModificationException(product.getId(), e);
        }
        return product;
    }

    @Override
    public Optional<ProductAggregate> findById(ProductId id, String tenantId) {
        return find("id = ?1 AND tenantId = ?2", id.getValue(), tenantId)
                .firstResultOptional()
                .map(mapper::toDomain);
    }

    @Override
    public Optional<ProductAggregate> findBySku(String sku, String tenantId) {
        return find("sku = ?1 AND tenantId = ?2", sku, tenantId)
                .firstResultOptional()
                .map(mapper::toDomain);
    }

    @Override
    @Transactional
    public void delete(ProductId id, String tenantId) {
        delete("id = ?1 AND tenantId = ?2", id.getValue(), tenantId);
    }

    @Override
    public boolean exists(ProductId id, String tenantId) {
        return count("id = ?1 AND tenantId = ?2", id.getValue(), tenantId) > 0;
    }

    // ── ProductQuery (read) ───────────────────────────────────────────────

    @Override
    public ProductPage findAll(String tenantId, int page, int size) {
        var query = find("tenantId = ?1", Sort.descending("updatedAt"), tenantId);
        long total = query.count();
        List<ProductAggregate> items = query
                .page(Page.of(page, size))
                .list()
                .stream().map(mapper::toDomain).toList();
        return new ProductPage(items, total, page, size);
    }

    @Override
    public ProductPage findByStatus(String tenantId, ProductStatus status, int page, int size) {
        var query = find("tenantId = ?1 AND status = ?2",
                Sort.descending("updatedAt"), tenantId, status.name());
        long total = query.count();
        List<ProductAggregate> items = query
                .page(Page.of(page, size))
                .list()
                .stream().map(mapper::toDomain).toList();
        return new ProductPage(items, total, page, size);
    }

    @Override
    public ProductPage findByCategory(String tenantId, String categoryId, int page, int size) {
        var query = find("tenantId = ?1 AND categoryId = ?2 AND status = ?3",
                Sort.descending("name"), tenantId, categoryId, ProductStatus.ACTIVE.name());
        long total = query.count();
        List<ProductAggregate> items = query
                .page(Page.of(page, size))
                .list()
                .stream().map(mapper::toDomain).toList();
        return new ProductPage(items, total, page, size);
    }

    @Override
    public ProductPage findByContext(String tenantId, String context, int page, int size) {
        // JSONB existence operator: extensions ? 'ecommerce'
        var query = find(
                "tenantId = ?1 AND function('jsonb_exists', extensions, ?2) = true",
                tenantId, context);
        long total = query.count();
        List<ProductAggregate> items = query
                .page(Page.of(page, size))
                .list()
                .stream().map(mapper::toDomain).toList();
        return new ProductPage(items, total, page, size);
    }

    @Override
    public ProductPage search(String tenantId, String searchQuery, int page, int size) {
        // PostgreSQL full-text search using ts_vector on name + description
        // Requires GIN index — see V2__product_search_index.sql
        String sql = """
            SELECT p FROM ProductJpaEntity p
            WHERE p.tenantId = ?1
              AND to_tsvector('english', coalesce(p.name,'') || ' ' || coalesce(p.description,''))
                  @@ plainto_tsquery('english', ?2)
            ORDER BY p.name
            """;
        List<ProductAggregate> items = getEntityManager()
                .createQuery(sql, ProductJpaEntity.class)
                .setParameter(1, tenantId)
                .setParameter(2, searchQuery)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList()
                .stream().map(mapper::toDomain).toList();

        // Count query
        String countSql = """
            SELECT count(p) FROM ProductJpaEntity p
            WHERE p.tenantId = ?1
              AND to_tsvector('english', coalesce(p.name,'') || ' ' || coalesce(p.description,''))
                  @@ plainto_tsquery('english', ?2)
            """;
        long total = getEntityManager()
                .createQuery(countSql, Long.class)
                .setParameter(1, tenantId)
                .setParameter(2, searchQuery)
                .getSingleResult();

        return new ProductPage(items, total, page, size);
    }

    @Override
    public List<ProductAggregate> findBySkuPrefix(String tenantId, String skuPrefix, int limit) {
        return find("tenantId = ?1 AND sku LIKE ?2",
                Sort.ascending("sku"), tenantId, skuPrefix + "%")
                .page(Page.ofSize(limit))
                .list()
                .stream().map(mapper::toDomain).toList();
    }
}

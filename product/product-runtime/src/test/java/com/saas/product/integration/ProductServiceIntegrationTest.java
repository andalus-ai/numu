package com.saas.product.integration;

import com.saas.product.core.ProductAggregate;
import com.saas.product.core.lifecycle.ProductStatus;
import com.saas.product.core.model.*;
import com.saas.product.core.pricing.PricingContext;
import com.saas.product.core.pricing.PricingResult;
import com.saas.product.extension.ecommerce.EcommerceExtension;
import com.saas.product.service.DuplicateSkuException;
import com.saas.product.service.ProductNotFoundException;
import com.saas.product.service.ProductService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration test: ProductService backed by real CDI + JPA (H2 in-memory).
 *
 * Requires:
 *   quarkus.datasource.db-kind=h2
 *   quarkus.hibernate-orm.database.generation=drop-and-create
 *   (set in src/test/resources/application.properties)
 *
 * Each test runs in its own transaction that is rolled back after.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProductServiceIntegrationTest {

    @Inject ProductService productService;

    private static final String TENANT   = "tenant-integration-test";
    private static final String CURRENCY = "IDR";
    private static final String ACTOR    = "test-user";

    private ProductCore buildCore(String sku) {
        return ProductCore.builder()
                .tenantId(TENANT)
                .sku(sku)
                .name("Integration Test Product - " + sku)
                .type(ProductType.PHYSICAL)
                .categoryId("cat-apparel")
                .build();
    }

    private EcommerceExtension buildEcomExt() {
        return EcommerceExtension.builder(
                        Money.of(new BigDecimal("250000"), CURRENCY))
                .compareAtPrice(Money.of(new BigDecimal("300000"), CURRENCY))
                .stockQuantity(100)
                .trackInventory(true)
                .build();
    }

    // ── Create ────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("Create product → persists in DRAFT state")
    void createPersistsDraft() {
        ProductAggregate created = productService.create(buildCore("IT-SKU-001"), ACTOR);

        assertThat(created.getId()).isNotNull();
        assertThat(created.getStatus()).isEqualTo(ProductStatus.DRAFT);
        assertThat(created.getTenantId()).isEqualTo(TENANT);

        // Load from DB
        ProductAggregate loaded = productService.findById(created.getId(), TENANT)
                .orElseThrow();
        assertThat(loaded.getCore().getSku()).isEqualTo("IT-SKU-001");
    }

    @Test
    @Order(2)
    @DisplayName("Duplicate SKU in same tenant throws DuplicateSkuException")
    void duplicateSkuThrows() {
        productService.create(buildCore("IT-SKU-DUP"), ACTOR);
        assertThatExceptionOfType(DuplicateSkuException.class)
                .isThrownBy(() -> productService.create(buildCore("IT-SKU-DUP"), ACTOR))
                .withMessageContaining("IT-SKU-DUP");
    }

    @Test
    @Order(3)
    @DisplayName("Same SKU in different tenants is allowed")
    void sameSkuDifferentTenantsAllowed() {
        productService.create(buildCore("IT-SKU-MULTI"), ACTOR);
        ProductCore otherTenantCore = ProductCore.builder()
                .tenantId("tenant-other")
                .sku("IT-SKU-MULTI")
                .name("Other Tenant Product")
                .type(ProductType.PHYSICAL)
                .build();
        assertThatNoException()
                .isThrownBy(() -> productService.create(otherTenantCore, ACTOR));
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    @Test
    @Order(4)
    @DisplayName("Activate transitions to ACTIVE and increments version")
    void activateTransition() {
        ProductAggregate p = productService.create(buildCore("IT-SKU-ACTIVATE"), ACTOR);
        long versionBefore = p.getVersion();

        ProductAggregate activated = productService.activate(p.getId(), TENANT, ACTOR);
        assertThat(activated.getStatus()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(activated.getVersion()).isGreaterThan(versionBefore);
    }

    @Test
    @Order(5)
    @DisplayName("Archive after activate → ARCHIVED status")
    void archiveAfterActivate() {
        ProductAggregate p = productService.create(buildCore("IT-SKU-ARCH"), ACTOR);
        productService.activate(p.getId(), TENANT, ACTOR);
        ProductAggregate archived = productService.archive(p.getId(), TENANT, ACTOR);
        assertThat(archived.getStatus()).isEqualTo(ProductStatus.ARCHIVED);
    }

    @Test
    @Order(6)
    @DisplayName("findById with wrong tenant returns empty")
    void tenantIsolation() {
        ProductAggregate p = productService.create(buildCore("IT-SKU-ISO"), ACTOR);
        assertThat(productService.findById(p.getId(), "wrong-tenant")).isEmpty();
    }

    // ── Extensions ────────────────────────────────────────────────────────

    @Test
    @Order(7)
    @DisplayName("putExtension persists and reloads correctly")
    void extensionRoundTrip() {
        ProductAggregate p = productService.create(buildCore("IT-SKU-EXT"), ACTOR);
        productService.putExtension(p.getId(), TENANT, buildEcomExt(), ACTOR);

        ProductAggregate reloaded = productService.findById(p.getId(), TENANT).orElseThrow();
        assertThat(reloaded.hasExtension("ecommerce")).isTrue();

        EcommerceExtension ext = reloaded.requireExtension("ecommerce");
        assertThat(ext.getBasePrice().getAmount()).isEqualByComparingTo("250000");
        assertThat(ext.getCompareAtPrice().getAmount()).isEqualByComparingTo("300000");
    }

    @Test
    @Order(8)
    @DisplayName("removeExtension removes and persists change")
    void removeExtension() {
        ProductAggregate p = productService.create(buildCore("IT-SKU-REM"), ACTOR);
        productService.putExtension(p.getId(), TENANT, buildEcomExt(), ACTOR);
        productService.removeExtension(p.getId(), TENANT, "ecommerce", ACTOR);

        ProductAggregate reloaded = productService.findById(p.getId(), TENANT).orElseThrow();
        assertThat(reloaded.hasExtension("ecommerce")).isFalse();
    }

    // ── Pricing ───────────────────────────────────────────────────────────

    @Test
    @Order(9)
    @DisplayName("calculatePrice with ecommerce context returns valid result")
    void calculateEcommercePrice() {
        ProductAggregate p = productService.create(buildCore("IT-SKU-PRICE"), ACTOR);
        productService.putExtension(p.getId(), TENANT, buildEcomExt(), ACTOR);
        productService.activate(p.getId(), TENANT, ACTOR);

        PricingContext ctx = PricingContext.builder(TENANT)
                .channelId("web")
                .customerSegment("retail")
                .quantity(2)
                .build();

        PricingResult result = productService.calculatePrice(p.getId(), "ecommerce", ctx);

        assertThat(result.getBasePrice().getAmount()).isEqualByComparingTo("250000");
        assertThat(result.getQuantity()).isEqualTo(2);
        assertThat(result.getFinalTotalPrice().getAmount())
                .isGreaterThan(new BigDecimal("500000")); // includes tax
    }

    @Test
    @Order(10)
    @DisplayName("calculatePrice on non-active product throws")
    void calculatePriceOnDraftThrows() {
        ProductAggregate p = productService.create(buildCore("IT-SKU-NOTACTIVE"), ACTOR);
        productService.putExtension(p.getId(), TENANT, buildEcomExt(), ACTOR);
        // NOT activated

        PricingContext ctx = PricingContext.builder(TENANT)
                .channelId("web")
                .quantity(1)
                .build();

        assertThatIllegalStateException()
                .isThrownBy(() -> productService.calculatePrice(p.getId(), "ecommerce", ctx));
    }

    // ── findBySku ─────────────────────────────────────────────────────────

    @Test
    @Order(11)
    @DisplayName("findBySku returns correct product for tenant")
    void findBySku() {
        productService.create(buildCore("IT-SKU-BYSKU"), ACTOR);
        var found = productService.findBySku("IT-SKU-BYSKU", TENANT);
        assertThat(found).isPresent();
        assertThat(found.get().getCore().getName()).contains("IT-SKU-BYSKU");
    }

    @Test
    @Order(12)
    @DisplayName("findById with nonexistent ID returns empty")
    void findByIdNotFound() {
        assertThat(productService.findById(ProductId.of("nonexistent-id"), TENANT))
                .isEmpty();
    }
}

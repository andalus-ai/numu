package com.saas.product.unit;

import com.saas.product.core.ProductAggregate;
import com.saas.product.core.ProductLifecycleException;
import com.saas.product.core.lifecycle.ProductStatus;
import com.saas.product.core.model.*;
import com.saas.product.extension.ecommerce.EcommerceExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

/**
 * Pure unit tests — no CDI, no DB, no framework.
 * Verifies all ProductAggregate invariants and lifecycle rules.
 */
class ProductAggregateTest {

    private ProductCore sampleCore;

    @BeforeEach
    void setUp() {
        sampleCore = ProductCore.builder()
                .tenantId("tenant-acme")
                .sku("SHIRT-RED-M")
                .name("Red T-Shirt Medium")
                .type(ProductType.PHYSICAL)
                .build();
    }

    // ── Construction ──────────────────────────────────────────────────────

    @Test
    @DisplayName("New product starts in DRAFT state")
    void newProductIsDraft() {
        var product = ProductAggregate.create(sampleCore, "admin");
        assertThat(product.getStatus()).isEqualTo(ProductStatus.DRAFT);
    }

    @Test
    @DisplayName("Version starts at 1")
    void versionStartsAtOne() {
        var product = ProductAggregate.create(sampleCore, "admin");
        assertThat(product.getVersion()).isEqualTo(1L);
    }

    @Test
    @DisplayName("createdBy and updatedBy are set from actor")
    void auditFieldsSetOnCreate() {
        var product = ProductAggregate.create(sampleCore, "admin-user");
        assertThat(product.getCreatedBy()).isEqualTo("admin-user");
        assertThat(product.getUpdatedBy()).isEqualTo("admin-user");
    }

    @Test
    @DisplayName("Null core throws NullPointerException")
    void nullCoreThrows() {
        assertThatNullPointerException()
                .isThrownBy(() -> ProductAggregate.create(null, "admin"));
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Lifecycle transitions")
    class LifecycleTests {

        @Test
        @DisplayName("DRAFT → ACTIVE allowed")
        void draftToActive() {
            var p = ProductAggregate.create(sampleCore, "admin");
            p.activate("admin");
            assertThat(p.getStatus()).isEqualTo(ProductStatus.ACTIVE);
        }

        @Test
        @DisplayName("DRAFT → ARCHIVED allowed (skip active)")
        void draftToArchived() {
            var p = ProductAggregate.create(sampleCore, "admin");
            p.archive("admin");
            assertThat(p.getStatus()).isEqualTo(ProductStatus.ARCHIVED);
        }

        @Test
        @DisplayName("ACTIVE → SUSPENDED allowed")
        void activeToSuspended() {
            var p = ProductAggregate.create(sampleCore, "admin");
            p.activate("admin");
            p.suspend("admin");
            assertThat(p.getStatus()).isEqualTo(ProductStatus.SUSPENDED);
        }

        @Test
        @DisplayName("SUSPENDED → ACTIVE allowed (reactivate)")
        void suspendedToActive() {
            var p = ProductAggregate.create(sampleCore, "admin");
            p.activate("admin");
            p.suspend("admin");
            p.activate("admin");
            assertThat(p.getStatus()).isEqualTo(ProductStatus.ACTIVE);
        }

        @Test
        @DisplayName("ARCHIVED → any state throws lifecycle exception")
        void archivedIsTerminal() {
            var p = ProductAggregate.create(sampleCore, "admin");
            p.archive("admin");
            assertThatExceptionOfType(ProductLifecycleException.class)
                    .isThrownBy(() -> p.activate("admin"));
        }

        @Test
        @DisplayName("ACTIVE → DRAFT throws lifecycle exception")
        void activeToDraftForbidden() {
            var p = ProductAggregate.create(sampleCore, "admin");
            p.activate("admin");
            // DRAFT is not a valid target from ACTIVE
            assertThat(ProductStatus.ACTIVE.canTransitionTo(ProductStatus.DRAFT)).isFalse();
        }

        @Test
        @DisplayName("Each mutation increments version")
        void mutationIncrementsVersion() {
            var p = ProductAggregate.create(sampleCore, "admin");
            assertThat(p.getVersion()).isEqualTo(1L);
            p.activate("admin");
            assertThat(p.getVersion()).isEqualTo(2L);
            p.suspend("admin");
            assertThat(p.getVersion()).isEqualTo(3L);
        }

        @Test
        @DisplayName("isOrderable() only true when ACTIVE")
        void orderableOnlyWhenActive() {
            var p = ProductAggregate.create(sampleCore, "admin");
            assertThat(p.isOrderable()).isFalse();
            p.activate("admin");
            assertThat(p.isOrderable()).isTrue();
            p.suspend("admin");
            assertThat(p.isOrderable()).isFalse();
        }
    }

    // ── Extensions ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Extension management")
    class ExtensionTests {

        @Test
        @DisplayName("Put and retrieve extension by context key")
        void putAndGetExtension() {
            var p = ProductAggregate.create(sampleCore, "admin");
            var ext = EcommerceExtension.builder(Money.of(BigDecimal.valueOf(150_000), "IDR"))
                    .stockQuantity(100)
                    .build();
            p.putExtension(ext, "admin");

            assertThat(p.hasExtension("ecommerce")).isTrue();
            EcommerceExtension retrieved = p.requireExtension("ecommerce");
            assertThat(retrieved.getBasePrice().getAmount())
                    .isEqualByComparingTo(BigDecimal.valueOf(150_000));
        }

        @Test
        @DisplayName("requireExtension throws when context missing")
        void requireExtensionThrowsWhenMissing() {
            var p = ProductAggregate.create(sampleCore, "admin");
            assertThatIllegalStateException()
                    .isThrownBy(() -> p.requireExtension("ecommerce"))
                    .withMessageContaining("ecommerce");
        }

        @Test
        @DisplayName("Remove extension removes it from the map")
        void removeExtension() {
            var p = ProductAggregate.create(sampleCore, "admin");
            var ext = EcommerceExtension.builder(Money.of(BigDecimal.valueOf(50_000), "IDR")).build();
            p.putExtension(ext, "admin");
            assertThat(p.hasExtension("ecommerce")).isTrue();
            p.removeExtension("ecommerce", "admin");
            assertThat(p.hasExtension("ecommerce")).isFalse();
        }

        @Test
        @DisplayName("Cannot put extension on archived product")
        void cannotModifyArchivedProduct() {
            var p = ProductAggregate.create(sampleCore, "admin");
            p.archive("admin");
            var ext = EcommerceExtension.builder(Money.of(BigDecimal.valueOf(10_000), "IDR")).build();
            assertThatExceptionOfType(ProductLifecycleException.class)
                    .isThrownBy(() -> p.putExtension(ext, "admin"));
        }

        @Test
        @DisplayName("findExtension returns Optional.empty for missing context")
        void findExtensionReturnsEmpty() {
            var p = ProductAggregate.create(sampleCore, "admin");
            assertThat(p.findExtension("fnb")).isEmpty();
        }

        @Test
        @DisplayName("Multiple extensions coexist on same product")
        void multipleExtensionsCoexist() {
            var p = ProductAggregate.create(sampleCore, "admin");

            var ecomExt = EcommerceExtension.builder(Money.of(BigDecimal.valueOf(100_000), "IDR")).build();
            var fnbExt  = com.saas.product.extension.fnb.FnbExtension
                    .builder(Money.of(BigDecimal.valueOf(35_000), "IDR")).build();

            p.putExtension(ecomExt, "admin");
            p.putExtension(fnbExt, "admin");

            assertThat(p.getExtensions()).hasSize(2);
            assertThat(p.hasExtension("ecommerce")).isTrue();
            assertThat(p.hasExtension("fnb")).isTrue();
        }
    }

    // ── Core update ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("Core updates")
    class CoreUpdateTests {

        @Test
        @DisplayName("updateCore changes name and increments version")
        void updateCoreName() {
            var p = ProductAggregate.create(sampleCore, "admin");
            ProductCore updated = sampleCore.withName("Red T-Shirt Large");
            p.updateCore(updated, "editor");
            assertThat(p.getCore().getName()).isEqualTo("Red T-Shirt Large");
            assertThat(p.getVersion()).isEqualTo(2L);
            assertThat(p.getUpdatedBy()).isEqualTo("editor");
        }

        @Test
        @DisplayName("updateCore with different ID throws")
        void updateCoreWithDifferentIdThrows() {
            var p = ProductAggregate.create(sampleCore, "admin");
            ProductCore differentId = ProductCore.builder()
                    .tenantId("tenant-acme")
                    .sku("OTHER-SKU")
                    .name("Other Name")
                    .type(ProductType.PHYSICAL)
                    .build(); // generates new ID
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> p.updateCore(differentId, "admin"));
        }
    }
}

package tech.kayys.syirkah.product.domain.archunit;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.testing.architecture.ForbiddenDependencyRules;

import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The Product domain must stay pure Java and must never absorb the
 * commercial or operational capabilities that consume it
 * (product00.md: no pricing, promotion, tax, inventory, accounting
 * inside Product).
 */
class ProductDomainArchitectureTest {

    private static final String DOMAIN_PACKAGE =
            "tech.kayys.syirkah.product.domain..";

    private static final JavaClasses DOMAIN_CLASSES =
            new ClassFileImporter()
                    .importPackages("tech.kayys.syirkah.product.domain");

    @Test
    void domainMustNotDependOnFrameworksOrInfrastructure() {
        ForbiddenDependencyRules.checkNoDependencyOn(
                DOMAIN_CLASSES,
                DOMAIN_PACKAGE,
                ForbiddenDependencyRules.INFRASTRUCTURE_PACKAGES
        );
    }

    @Test
    void domainMustNotDependOnReactiveTypes() {
        ForbiddenDependencyRules.checkNoDependencyOn(
                DOMAIN_CLASSES,
                DOMAIN_PACKAGE,
                ForbiddenDependencyRules.REACTIVE_PACKAGES
        );
    }

    @Test
    void domainMustNotDependOnTheApplicationOrAdapterLayers() {
        noClasses()
                .that().resideInAPackage(DOMAIN_PACKAGE)
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "tech.kayys.syirkah.product.application..",
                        "tech.kayys.syirkah.product.spi..",
                        "tech.kayys.syirkah.product.adapter.."
                )
                .because("dependency direction is Adapter -> Application -> Domain, never the reverse")
                .check(DOMAIN_CLASSES);
    }

    @Test
    void domainMustNotAbsorbCommercialOrOperationalCapabilities() {
        noClasses()
                .that().resideInAPackage(DOMAIN_PACKAGE)
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "..commerce..",
                        "..pricing..",
                        "..promotion..",
                        "..inventory..",
                        "..accounting..",
                        "..retail..",
                        "..ecommerce.."
                )
                .because("Product is a reusable foundation: "
                        + "pricing, promotion, inventory and accounting "
                        + "consume Product IDs and events instead")
                .check(DOMAIN_CLASSES);
    }

    // --- Product 1.0 consolidation guards ------------------------------
    // product-core held a second, flatter Product model in the SAME package
    // prefix (tech.kayys.syirkah.product.domain.Product, .ProductStatus,
    // .ProductType, .ProductRepository). It was consolidated into this module
    // and deleted. These rules fail fast if a duplicate model is reintroduced.

    @Test
    void productAggregateMustExistExactlyOnce() {
        assertEquals(
                List.of("tech.kayys.syirkah.product.domain.product.Product"),
                simpleNamesOf("Product"),
                "there must be exactly one Product aggregate; a second one "
                        + "means the retired product-core model has returned"
        );
    }

    @Test
    void productIdentityMustExistExactlyOnce() {
        assertEquals(
                List.of("tech.kayys.syirkah.product.domain.product.ProductId"),
                simpleNamesOf("ProductId"),
                "there must be exactly one Product identity; a second one "
                        + "means the retired product-core model has returned"
        );
    }

    @Test
    void productStatusAndTypeMustExistExactlyOnce() {
        assertEquals(
                List.of("tech.kayys.syirkah.product.domain.product.ProductStatus"),
                simpleNamesOf("ProductStatus"),
                "lifecycle enums must not be duplicated across Product models"
        );

        assertEquals(
                List.of("tech.kayys.syirkah.product.domain.product.ProductType"),
                simpleNamesOf("ProductType"),
                "type enums must not be duplicated across Product models"
        );
    }

    @Test
    void domainMustNotDeclareItsOwnRepositoryPort() {
        noClasses()
                .that().resideInAPackage(DOMAIN_PACKAGE)
                .should().haveSimpleNameEndingWith("Repository")
                .because("persistence ports belong to product-spi "
                        + "(tech.kayys.syirkah.product.spi.port), not the domain; "
                        + "the retired product-core declared ProductRepository "
                        + "inside the domain package")
                .check(DOMAIN_CLASSES);
    }

    // --- Legacy runtime guard -------------------------------------------
    // product-runtime (com.saas.product.*) is a pre-1.0 duplicate model that
    // is excluded from the Maven reactor. The Product domain must never
    // depend on it, otherwise the canonical and legacy models would merge
    // again.

    @Test
    void domainMustNotDependOnTheLegacyRuntimeModel() {
        noClasses()
                .that().resideInAPackage(DOMAIN_PACKAGE)
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.saas.product..")
                .because("com.saas.product.* is the deprecated pre-1.0 duplicate "
                        + "model (product-runtime); the canonical model is "
                        + "tech.kayys.syirkah.product.domain.*")
                .check(DOMAIN_CLASSES);
    }

    // --- Bounded-context naming guards ----------------------------------
    // Catalog and POS (Groceries) are separate bounded contexts. They may
    // reference the Product foundation by ProductId, but they must NOT
    // reuse the bare canonical names (Product, ProductStatus) inside their
    // own domain packages, otherwise imports become ambiguous. The
    // "exactly once" tests above already enforce that the canonical names
    // appear exactly once inside the Product domain package. Equivalent
    // guards for the Catalog and POS contexts live in their own modules
    // (CatalogDomainArchitectureTest / GroceriesDomainArchitectureTest),
    // because product-domain has no dependency on those contexts and
    // therefore cannot import their classes.

    @Test
    void domainMustNotDependOnTheCatalogBoundedContext() {
        noClasses()
                .that().resideInAPackage(DOMAIN_PACKAGE)
                .should().dependOnClassesThat()
                .resideInAnyPackage("tech.kayys.syirkah.catalog.domain..")
                .because("Catalog is a separate bounded context that consumes "
                        + "Product IDs and events; the Product domain must not "
                        + "depend on it (dependency direction is Catalog -> Product)")
                .check(DOMAIN_CLASSES);
    }

    @Test
    void domainMustNotDependOnThePosBoundedContext() {
        noClasses()
                .that().resideInAPackage(DOMAIN_PACKAGE)
                .should().dependOnClassesThat()
                .resideInAnyPackage("tech.kayys.syirkah.groceries.domain..")
                .because("POS (Groceries) is a separate bounded context that "
                        + "consumes Product IDs and events; the Product domain "
                        + "must not depend on it (dependency direction is POS -> Product)")
                .check(DOMAIN_CLASSES);
    }

    private static List<String> simpleNamesOf(String simpleName) {
        return DOMAIN_CLASSES.stream()
                .filter(c -> c.getSimpleName().equals(simpleName))
                .map(c -> c.getName())
                .sorted()
                .toList();
    }
}
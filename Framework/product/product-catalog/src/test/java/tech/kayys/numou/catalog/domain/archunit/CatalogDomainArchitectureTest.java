package tech.kayys.syirkah.catalog.domain.archunit;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Architecture guard for the Catalog bounded context.
 *
 * <p>Catalog is a <b>separate</b> bounded context from the Product
 * foundation. It references the foundation by
 * {@code tech.kayys.syirkah.product.domain.product.ProductId} and
 * consumes Product events — it must never shadow the canonical
 * Product names, otherwise imports become ambiguous across the two
 * contexts.</p>
 */
class CatalogDomainArchitectureTest {

    private static final String CATALOG_PACKAGE =
            "tech.kayys.syirkah.catalog.domain..";

    private static final JavaClasses CATALOG_CLASSES =
            new ClassFileImporter()
                    .importPackages("tech.kayys.syirkah.catalog.domain");

    @Test
    void catalogMustNotReuseTheBareCanonicalProductAggregateName() {
        assertEquals(
                List.of("tech.kayys.syirkah.catalog.domain.model.CatalogProduct"),
                fullyQualifiedNamesOf(CATALOG_CLASSES, "CatalogProduct"),
                "Catalog must expose CatalogProduct, not the bare name Product; "
                        + "the bare name would clash with "
                        + "tech.kayys.syirkah.product.domain.product.Product"
        );
    }

    @Test
    void catalogMustNotReuseTheBareCanonicalProductStatusName() {
        assertEquals(
                List.of("tech.kayys.syirkah.catalog.domain.valueobject.CatalogProductStatus"),
                fullyQualifiedNamesOf(CATALOG_CLASSES, "CatalogProductStatus"),
                "Catalog must expose CatalogProductStatus, not the bare name "
                        + "ProductStatus; the bare name would clash with "
                        + "tech.kayys.syirkah.product.domain.product.ProductStatus"
        );
    }

    @Test
    void catalogMustNotDependOnTheProductAggregate() {
        // Catalog is allowed to reference ProductId (a shared stable
        // identifier) and to consume Product events, but it must not
        // reach into the Product aggregate root itself.
        noClasses()
                .that().resideInAPackage(CATALOG_PACKAGE)
                .should().dependOnClassesThat()
                .haveFullyQualifiedName("tech.kayys.syirkah.product.domain.product.Product")
                .because("Catalog references the Product foundation by ProductId "
                        + "and consumes its events, but must not depend on the "
                        + "Product aggregate root itself")
                .check(CATALOG_CLASSES);
    }

    private static List<String> fullyQualifiedNamesOf(JavaClasses classes, String simpleName) {
        return classes.stream()
                .filter(c -> c.getSimpleName().equals(simpleName))
                .map(c -> c.getName())
                .sorted()
                .toList();
    }
}
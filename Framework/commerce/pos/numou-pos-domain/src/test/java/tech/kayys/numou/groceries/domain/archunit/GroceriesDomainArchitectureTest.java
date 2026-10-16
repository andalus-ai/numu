package tech.kayys.syirkah.groceries.domain.archunit;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Architecture guard for the POS (Groceries) bounded context.
 *
 * <p>POS (Groceries) is a <b>separate</b> bounded context from the
 * Product foundation. It references a product that lives in the
 * Catalog / Product bounded context by {@code catalogProductId} and by
 * its own context-local
 * {@code tech.kayys.syirkah.groceries.domain.identifier.ProductId}.
 * It must never reuse the bare canonical Product aggregate name,
 * otherwise imports become ambiguous across the two contexts.</p>
 */
class GroceriesDomainArchitectureTest {

    private static final JavaClasses GROCERIES_CLASSES =
            new ClassFileImporter()
                    .importPackages("tech.kayys.syirkah.groceries.domain");

    @Test
    void posMustNotReuseTheBareCanonicalProductAggregateName() {
        assertEquals(
                List.of("tech.kayys.syirkah.groceries.domain.model.PosProduct"),
                fullyQualifiedNamesOf(GROCERIES_CLASSES, "PosProduct"),
                "POS (Groceries) must expose PosProduct, not the bare name "
                        + "Product; the bare name would clash with "
                        + "tech.kayys.syirkah.product.domain.product.Product"
        );
    }

    @Test
    void posProductIdMustRemainContextLocal() {
        assertEquals(
                List.of("tech.kayys.syirkah.groceries.domain.identifier.ProductId"),
                fullyQualifiedNamesOf(GROCERIES_CLASSES, "ProductId"),
                "POS (Groceries) keeps its own context-local ProductId "
                        + "(a reference to a product that lives in the Catalog / "
                        + "Product bounded context). It must not be replaced by "
                        + "tech.kayys.syirkah.product.domain.product.ProductId, "
                        + "otherwise cross-context identity mixing becomes possible."
        );
    }

    private static List<String> fullyQualifiedNamesOf(JavaClasses classes, String simpleName) {
        return classes.stream()
                .filter(c -> c.getSimpleName().equals(simpleName))
                .map(c -> c.getName())
                .sorted()
                .toList();
    }
}
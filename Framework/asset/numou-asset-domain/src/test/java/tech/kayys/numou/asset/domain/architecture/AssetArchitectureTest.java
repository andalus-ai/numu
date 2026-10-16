package tech.kayys.syirkah.asset.domain.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architecture tests for the Asset bounded context (plan section 40).
 *
 * <p>Guards the dependency direction
 * {@code domain <- application/spi <- adapter} and keeps the domain
 * framework-free: no Quarkus, no JPA, no REST leakage into the domain.</p>
 */
@DisplayName("Asset architecture")
@AnalyzeClasses(
        packages = "tech.kayys.syirkah.asset.domain",
        importOptions = ImportOption.DoNotIncludeTests.class)
class AssetArchitectureTest {

    @ArchTest
    static final ArchRule domainDoesNotDependOnOuterLayers = noClasses()
            .that().resideInAPackage("tech.kayys.syirkah.asset.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "tech.kayys.syirkah.asset.application..",
                    "tech.kayys.syirkah.asset.spi..",
                    "tech.kayys.syirkah.asset.infrastructure..",
                    "tech.kayys.syirkah.asset.interfaces..",
                    "io.quarkus..",
                    "jakarta.persistence..",
                    "jakarta.ws.rs..")
            .because("the Asset domain must stay framework-free (ASSET-01)");

    @ArchTest
    static final ArchRule domainOnlyUsesFoundationPrimitives = noClasses()
            .that().resideInAPackage("tech.kayys.syirkah.asset.domain..")
            .should().dependOnClassesThat()
            .resideOutsideOfPackages(
                    "tech.kayys.syirkah.asset.domain..",
                    "tech.kayys.syirkah.foundation..",
                    "java..",
                    "org.slf4j..")
            .allowEmptyShould(true)
            .because("the domain may use foundation primitives and the JDK only (ASSET-01)");

    @Test
    @DisplayName("aggregate raises its lifecycle events")
    void aggregateRaisesLifecycleEvents() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("tech.kayys.syirkah.asset.domain.model");

        long raiseCalls = classes.stream()
                .flatMap(clazz -> clazz.getMethodCallsFromSelf().stream())
                .filter(call -> call.getTarget().getName().equals("raise"))
                .count();

        org.junit.jupiter.api.Assertions.assertTrue(
                raiseCalls > 0,
                "Asset must raise domain events through AbstractAggregateRoot.raise");
    }
}

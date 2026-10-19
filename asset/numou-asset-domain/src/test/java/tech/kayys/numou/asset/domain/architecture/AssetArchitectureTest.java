package tech.kayys.numou.asset.domain.architecture;

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
        packages = "tech.kayys.numou.asset.domain",
        importOptions = ImportOption.DoNotIncludeTests.class)
class AssetArchitectureTest {

    @ArchTest
    static final ArchRule domainDoesNotDependOnOuterLayers = noClasses()
            .that().resideInAPackage("tech.kayys.numou.asset.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "tech.kayys.numou.asset.application..",
                    "tech.kayys.numou.asset.spi..",
                    "tech.kayys.numou.asset.infrastructure..",
                    "tech.kayys.numou.asset.interfaces..",
                    "io.quarkus..",
                    "jakarta.persistence..",
                    "jakarta.ws.rs..")
            .because("the Asset domain must stay framework-free (ASSET-01)");

    @ArchTest
    static final ArchRule domainOnlyUsesFoundationPrimitives = noClasses()
            .that().resideInAPackage("tech.kayys.numou.asset.domain..")
            .should().dependOnClassesThat()
            .resideOutsideOfPackages(
                    "tech.kayys.numou.asset.domain..",
                    "tech.kayys.numou.foundation..",
                    "java..",
                    "org.slf4j..")
            .allowEmptyShould(true)
            .because("the domain may use foundation primitives and the JDK only (ASSET-01)");

    @Test
    @DisplayName("aggregate raises its lifecycle events")
    void aggregateRaisesLifecycleEvents() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("tech.kayys.numou.asset.domain.model");

        long raiseCalls = classes.stream()
                .flatMap(clazz -> clazz.getMethodCallsFromSelf().stream())
                .filter(call -> call.getTarget().getName().equals("raise"))
                .count();

        org.junit.jupiter.api.Assertions.assertTrue(
                raiseCalls > 0,
                "Asset must raise domain events through AbstractAggregateRoot.raise");
    }
}

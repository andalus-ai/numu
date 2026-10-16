package tech.kayys.syirkah.project.domain.archunit;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.testing.architecture.ForbiddenDependencyRules;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * The Project domain layer must stay pure Java: no Quarkus,
 * Hibernate, Kafka, Jackson and no reactive types. Timestamps are
 * produced by the aggregates themselves (the Foundation
 * AbstractAggregateRoot does the same), so Mutiny must not leak in
 * here.
 */
class ProjectDomainArchitectureTest {

    private static final String DOMAIN_PACKAGE =
            "tech.kayys.syirkah.project.domain..";

    private static final JavaClasses DOMAIN_CLASSES =
            new ClassFileImporter()
                    .importPackages("tech.kayys.syirkah.project.domain");

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
                        "tech.kayys.syirkah.project.application..",
                        "tech.kayys.syirkah.project.spi..",
                        "tech.kayys.syirkah.project.adapter.."
                )
                .because("dependency direction is Adapter -> Application -> Domain, never the reverse")
                .check(DOMAIN_CLASSES);
    }
}

package tech.kayys.numou.support.domain.archunit;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.testing.architecture.ForbiddenDependencyRules;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * The Support domain (Ticket and its comments/status/priority) must stay
 * pure Java: no frameworks, no reactive types, and never a dependency
 * back on the application or adapter layers.
 */
class SupportDomainArchitectureTest {

    private static final String DOMAIN_PACKAGE =
            "tech.kayys.numou.support.domain..";

    private static final JavaClasses DOMAIN_CLASSES =
            new ClassFileImporter()
                    .importPackages("tech.kayys.numou.support.domain");

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
                        "tech.kayys.numou.support.application..",
                        "tech.kayys.numou.support.api.."
                )
                .because("dependency direction is Adapter -> Application -> Domain, never the reverse")
                .check(DOMAIN_CLASSES);
    }
}

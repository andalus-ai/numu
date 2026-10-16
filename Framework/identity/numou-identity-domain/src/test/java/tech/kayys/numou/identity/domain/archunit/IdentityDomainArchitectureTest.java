package tech.kayys.numou.identity.domain.archunit;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.testing.architecture.ForbiddenDependencyRules;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Same rule as numou-foundation: the Identity domain layer stays
 * pure Java. Unlike foundation/domain, this module is free to use
 * numou-foundation-testing (it does not create a reactor cycle), so it
 * reuses the shared forbidden-package list instead of duplicating it.
 */
class IdentityDomainArchitectureTest {

        private static final String DOMAIN_PACKAGE = "tech.kayys.numou.identity.domain..";

        private static final JavaClasses DOMAIN_CLASSES = new ClassFileImporter()
                        .importPackages("tech.kayys.numou.identity.domain");

        @Test
        void domainMustNotDependOnFrameworksOrInfrastructure() {
                ForbiddenDependencyRules.checkNoDependencyOn(
                                DOMAIN_CLASSES,
                                DOMAIN_PACKAGE,
                                ForbiddenDependencyRules.INFRASTRUCTURE_PACKAGES);
        }

        @Test
        void domainMustNotDependOnReactiveTypes() {
                ForbiddenDependencyRules.checkNoDependencyOn(
                                DOMAIN_CLASSES,
                                DOMAIN_PACKAGE,
                                ForbiddenDependencyRules.REACTIVE_PACKAGES);
        }

        @Test
        void domainMustNotDependOnTheApplicationLayer() {
                noClasses()
                                .that().resideInAPackage(DOMAIN_PACKAGE)
                                .should().dependOnClassesThat()
                                .resideInAPackage("tech.kayys.numou.identity.application..")
                                .because("dependency direction is Application -> Domain, never the reverse")
                                .check(DOMAIN_CLASSES);
        }

}

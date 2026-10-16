package tech.kayys.syirkah.identity.domain.archunit;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.testing.architecture.ForbiddenDependencyRules;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Same rule as syirkah-foundation: the Identity domain layer stays
 * pure Java. Unlike foundation/domain, this module is free to use
 * syirkah-foundation-testing (it does not create a reactor cycle), so it
 * reuses the shared forbidden-package list instead of duplicating it.
 */
class IdentityDomainArchitectureTest {

        private static final String DOMAIN_PACKAGE = "tech.kayys.syirkah.identity.domain..";

        private static final JavaClasses DOMAIN_CLASSES = new ClassFileImporter()
                        .importPackages("tech.kayys.syirkah.identity.domain");

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
                                .resideInAPackage("tech.kayys.syirkah.identity.application..")
                                .because("dependency direction is Application -> Domain, never the reverse")
                                .check(DOMAIN_CLASSES);
        }

}

package tech.kayys.syirkah.identity.application.archunit;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.testing.architecture.ForbiddenDependencyRules;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class IdentityApplicationArchitectureTest {

    private static final String APPLICATION_PACKAGE =
            "tech.kayys.syirkah.identity.application..";

    private static final JavaClasses APPLICATION_CLASSES =
            new ClassFileImporter()
                    .importPackages("tech.kayys.syirkah.identity.application");

    @Test
    void applicationMustNotDependOnInfrastructure() {
        ForbiddenDependencyRules.checkNoDependencyOn(
                APPLICATION_CLASSES,
                APPLICATION_PACKAGE,
                ForbiddenDependencyRules.INFRASTRUCTURE_PACKAGES
        );
    }

    @Test
    void applicationMustNotDependOnTheAdapterLayer() {
        noClasses()
                .that().resideInAPackage(APPLICATION_PACKAGE)
                .should().dependOnClassesThat().resideInAPackage("tech.kayys.syirkah.identity.adapter..")
                .because("dependency direction is Adapter -> Application, never the reverse - "
                        + "ports are defined here and implemented there")
                .check(APPLICATION_CLASSES);
    }

}

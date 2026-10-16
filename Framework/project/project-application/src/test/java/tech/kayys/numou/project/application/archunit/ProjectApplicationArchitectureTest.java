package tech.kayys.syirkah.project.application.archunit;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.testing.architecture.ForbiddenDependencyRules;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * The Project application layer may orchestrate reactively (Mutiny is
 * explicitly allowed here, unlike in the domain) but it must never
 * reach for infrastructure or for the adapter layer that implements its
 * ports.
 */
class ProjectApplicationArchitectureTest {

    private static final String APPLICATION_PACKAGE =
            "tech.kayys.syirkah.project.application..";

    private static final JavaClasses APPLICATION_CLASSES =
            new ClassFileImporter()
                    .importPackages("tech.kayys.syirkah.project.application");

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
                .should().dependOnClassesThat()
                .resideInAPackage("tech.kayys.syirkah.project.adapter..")
                .because("dependency direction is Adapter -> Application, never the reverse - "
                        + "ports are defined in project-spi and implemented in the adapter")
                .check(APPLICATION_CLASSES);
    }
}

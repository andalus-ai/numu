package tech.kayys.numou.crm.application.archunit;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.testing.architecture.ForbiddenDependencyRules;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * The CRM application layer may orchestrate reactively (Mutiny is
 * explicitly allowed here, unlike in the domain) but must never reach
 * for infrastructure or for the adapter layer that implements its ports.
 */
class CrmApplicationArchitectureTest {

    private static final String APPLICATION_PACKAGE =
            "tech.kayys.numou.crm.application..";

    private static final JavaClasses APPLICATION_CLASSES =
            new ClassFileImporter()
                    .importPackages("tech.kayys.numou.crm.application");

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
                .resideInAnyPackage(
                        "tech.kayys.numou.crm.interfaces..",
                        "tech.kayys.numou.crm.infrastructure.."
                )
                .because("dependency direction is Adapter -> Application, never the reverse - "
                        + "ports are defined in the application layer and implemented in the adapter")
                .check(APPLICATION_CLASSES);
    }
}

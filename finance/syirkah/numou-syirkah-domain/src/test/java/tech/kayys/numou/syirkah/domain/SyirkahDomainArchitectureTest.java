package tech.kayys.numou.numoudomain;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** The syirkah domain stays pure Java: no frameworks, no outer layers. */
class SyirkahDomainArchitectureTest {

    private static final JavaClasses DOMAIN = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("tech.kayys.numou.numoudomain");

    private static final String[] FORBIDDEN = {
            "io.quarkus..", "org.hibernate..", "jakarta..", "io.smallrye..", "org.apache.kafka..",
            "com.fasterxml.jackson..", "tech.kayys.numou.numouapplication..", "tech.kayys.numou.numouadapter..",
            "tech.kayys.numou.accounting.."
    };

    @Test
    void domainMustNotDependOnFrameworksOrOuterLayers() {
        for (String pkg : FORBIDDEN) {
            noClasses().that().resideInAPackage("tech.kayys.numou.numoudomain..")
                    .should().dependOnClassesThat().resideInAPackage(pkg)
                    .check(DOMAIN);
        }
    }
}

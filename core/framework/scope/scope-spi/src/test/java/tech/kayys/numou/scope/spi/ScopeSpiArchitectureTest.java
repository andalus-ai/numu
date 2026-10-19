package tech.kayys.numou.scope.spi;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ScopeSpiArchitectureTest {
    @Test
    void scopePortsDoNotDependOnIndustryOrAdapterModules() {
        var classes = new ClassFileImporter().importPackages("tech.kayys.numou.scope.spi");

        noClasses()
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "tech.kayys.numou.construction..",
                        "tech.kayys.numou.project..",
                        "tech.kayys.numou.scope.application..",
                        "tech.kayys.numou.scope.adapter..",
                        "io.quarkus..",
                        "jakarta.persistence..",
                        "org.hibernate..",
                        "org.springframework..")
                .check(classes);
    }
}

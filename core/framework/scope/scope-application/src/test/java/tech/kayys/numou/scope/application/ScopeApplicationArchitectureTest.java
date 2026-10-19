package tech.kayys.numou.scope.application;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ScopeApplicationArchitectureTest {
    @Test
    void applicationDoesNotDependOnIndustryOrFrameworkAdapters() {
        var classes = new ClassFileImporter().importPackages("tech.kayys.numou.scope.application");

        noClasses()
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "tech.kayys.numou.construction..",
                        "tech.kayys.numou.project..",
                        "tech.kayys.numou.scope.adapter..",
                        "io.quarkus..",
                        "jakarta.persistence..",
                        "org.hibernate..",
                        "org.springframework..")
                .check(classes);
    }
}

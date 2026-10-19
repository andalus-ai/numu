package tech.kayys.numou.scope.domain;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ScopeDomainArchitectureTest {
    private static final String DOMAIN_PACKAGE = "tech.kayys.numou.scope.domain..";
    private static final JavaClasses DOMAIN_CLASSES =
            new ClassFileImporter().importPackages("tech.kayys.numou.scope.domain");

    @Test
    void scopeDomainDoesNotDependOnIndustryOrApplicationModules() {
        noClasses()
                .that().resideInAPackage(DOMAIN_PACKAGE)
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "tech.kayys.numou.construction..",
                        "tech.kayys.numou.project..",
                        "tech.kayys.numou.scope.application..",
                        "tech.kayys.numou.scope.spi..",
                        "tech.kayys.numou.scope.adapter..")
                .check(DOMAIN_CLASSES);
    }

    @Test
    void scopeDomainDoesNotDependOnFrameworksOrPersistence() {
        noClasses()
                .that().resideInAPackage(DOMAIN_PACKAGE)
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "io.quarkus..",
                        "jakarta.persistence..",
                        "org.hibernate..",
                        "io.smallrye.mutiny..",
                        "org.springframework..")
                .check(DOMAIN_CLASSES);
    }
}

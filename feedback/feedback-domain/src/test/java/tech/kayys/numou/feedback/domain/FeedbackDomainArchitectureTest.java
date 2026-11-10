package tech.kayys.numou.feedback.domain;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.JavaClasses;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class FeedbackDomainArchitectureTest {
    @Test
    void domainMustNotDependOnApplicationOrInfrastructure() {
        JavaClasses classes = new ClassFileImporter().importPackages("tech.kayys.numou.feedback.domain");

        noClasses().should().dependOnClassesThat()
                .resideInAnyPackage("..feedback.application..", "io.quarkus..",
                        "jakarta.persistence..", "org.hibernate..", "jakarta.ws.rs..",
                        "com.fasterxml.jackson..", "org.apache.kafka..")
                .check(classes);
    }
}
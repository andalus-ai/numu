package tech.kayys.numou.leaderboard.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ScoreLedgerDomainArchitectureTest {
    @Test
    void scoreLedgerDomainDoesNotDependOnInfrastructureFrameworks() {
        ArchRule rule = noClasses().that().resideInAPackage("tech.kayys.numou.leaderboard.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "jakarta..", "javax..", "io.quarkus..", "org.hibernate..",
                        "org.springframework..", "io.smallrye..", "org.apache.kafka..",
                        "com.fasterxml.jackson..");
        rule.check(new ClassFileImporter().importPackages("tech.kayys.numou.leaderboard.domain"));
    }
}
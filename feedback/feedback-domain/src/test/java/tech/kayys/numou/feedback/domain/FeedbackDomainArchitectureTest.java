package tech.kayys.numou.feedback.domain;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "tech.kayys.numou.feedback.domain",
        importOptions = ImportOption.DoNotIncludeTests.class)
class FeedbackDomainArchitectureTest {

    @ArchTest
    static final ArchRule domain_must_not_depend_on_transport_persistence_or_runtime =
            noClasses().should().dependOnClassesThat().resideInAnyPackage(
                    "jakarta.persistence..",
                    "org.hibernate..",
                    "jakarta.ws.rs..",
                    "io.quarkus..",
                    "org.apache.kafka..",
                    "org.springframework..");
}
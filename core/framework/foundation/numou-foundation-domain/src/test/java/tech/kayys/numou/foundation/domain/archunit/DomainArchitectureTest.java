package tech.kayys.numou.foundation.domain.archunit;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces the single most important rule of this whole foundation:
 * the domain layer stays pure Java forever.
 *
 * If any of these tests ever fail, it means someone added a runtime
 * dependency to foundation/domain/pom.xml and started leaning on it -
 * that is exactly the "distributed monolith disguised as microservices"
 * failure mode this platform is designed to avoid.
 */
class DomainArchitectureTest {

    private static final String DOMAIN_PACKAGE =
            "tech.kayys.numou.foundation.domain..";

    private static final JavaClasses DOMAIN_CLASSES =
            new ClassFileImporter()
                    .withImportOption(new ImportOption.DoNotIncludeTests())
                    .importPackages("tech.kayys.numou.foundation.domain");

    private static final String[] FORBIDDEN_PACKAGES = {
            "io.quarkus..",
            "org.hibernate..",
            "jakarta.persistence..",
            "jakarta.ws.rs..",
            "jakarta.enterprise..",
            "jakarta.inject..",
            "org.apache.kafka..",
            "io.smallrye.mutiny..",
            "io.smallrye.reactive..",
            "redis.clients..",
            "io.lettuce..",
            "com.fasterxml.jackson.."
    };

    @Test
    void domainMustNotDependOnFrameworksOrInfrastructure() {
        for (String forbiddenPackage : FORBIDDEN_PACKAGES) {
            noClasses()
                    .that().resideInAPackage(DOMAIN_PACKAGE)
                    .should().dependOnClassesThat().resideInAPackage(forbiddenPackage)
                    .because("the domain module must stay pure Java - "
                            + "see foundation/domain/pom.xml, which has zero "
                            + "runtime dependencies by design")
                    .check(DOMAIN_CLASSES);
        }
    }

    @Test
    void domainClassesMustOnlyLiveInKnownSubpackages() {
        classes()
                .that().resideInAPackage(DOMAIN_PACKAGE)
                .and().areTopLevelClasses()
                .should().resideInAnyPackage(
                        "tech.kayys.numou.foundation.domain.identifier..",
                        "tech.kayys.numou.foundation.domain.entity..",
                        "tech.kayys.numou.foundation.domain.valueobject..",
                        "tech.kayys.numou.foundation.domain.event..",
                        "tech.kayys.numou.foundation.domain.time..",
                        "tech.kayys.numou.foundation.domain.exception..",
                        "tech.kayys.numou.foundation.domain.repository..",
                        "tech.kayys.numou.foundation.domain.tenant..",
                        "tech.kayys.numou.foundation.domain.ref..",
                        "tech.kayys.numou.foundation.domain.audit..",
                        "tech.kayys.numou.foundation.domain.referencedata.."
                )
                .because("undisciplined packages are how a foundation module "
                        + "quietly turns into a junk-drawer 'common' library")
                .check(DOMAIN_CLASSES);
    }

}

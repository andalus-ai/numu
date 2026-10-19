package tech.kayys.numou.foundation.testing.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Shared ArchUnit rule library so every module's architecture test
 * enforces the same "no framework leakage" policy from one place,
 * instead of every domain/application module re-declaring (and
 * slowly drifting out of sync with) its own forbidden-package list.
 *
 * Not used by numou-foundation itself: that module is a compile
 * dependency of this one, so depending back on it here would create a
 * module cycle in the Maven reactor. numou-foundation keeps its
 * own self-contained architecture test for that reason.
 */
public final class ForbiddenDependencyRules {

        /**
         * Frameworks/technologies a pure domain or application layer must
         * never depend on directly - those belong to the adapters layer.
         */
        public static final String[] INFRASTRUCTURE_PACKAGES = {
                        "io.quarkus..",
                        "org.hibernate..",
                        "jakarta.persistence..",
                        "jakarta.ws.rs..",
                        "jakarta.enterprise..",
                        "jakarta.inject..",
                        "org.apache.kafka..",
                        "redis.clients..",
                        "io.lettuce..",
                        "com.fasterxml.jackson.."
        };

        /**
         * Additionally forbidden for the pure domain layer only - the
         * application layer is explicitly where reactive orchestration
         * (Mutiny) is allowed to live.
         */
        public static final String[] REACTIVE_PACKAGES = {
                        "io.smallrye.mutiny..",
                        "io.smallrye.reactive.."
        };

        private ForbiddenDependencyRules() {
        }

        /**
         * Asserts that no class in {@code packageUnderTest} depends on any
         * class in any of {@code forbiddenPackages}.
         */
        public static void checkNoDependencyOn(
                        JavaClasses classes,
                        String packageUnderTest,
                        String... forbiddenPackages) {
                for (String forbiddenPackage : forbiddenPackages) {
                        ArchRule rule = noClasses()
                                        .that().resideInAPackage(packageUnderTest)
                                        .should().dependOnClassesThat().resideInAPackage(forbiddenPackage)
                                        .because("'" + packageUnderTest + "' must not depend on '"
                                                        + forbiddenPackage + "' - that belongs to the adapters layer");

                        rule.check(classes);
                }
        }

}

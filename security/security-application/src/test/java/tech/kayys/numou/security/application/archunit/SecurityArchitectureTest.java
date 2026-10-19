package tech.kayys.numou.security.application.archunit;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "tech.kayys.numou.security", importOptions = ImportOption.DoNotIncludeTests.class)
public class SecurityArchitectureTest {

    @ArchTest
    public static final ArchRule securityMustNotDependOnIdentity =
            noClasses()
                    .that().resideInAPackage("tech.kayys.numou.security..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("tech.kayys.numou.identity..");

    @ArchTest
    public static final ArchRule securityDomainMustNotDependOnApplication =
            noClasses()
                    .that().resideInAPackage("tech.kayys.numou.security.domain..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("tech.kayys.numou.security.application..");

    @ArchTest
    public static final ArchRule securityDomainMustNotDependOnSpi =
            noClasses()
                    .that().resideInAPackage("tech.kayys.numou.security.domain..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("tech.kayys.numou.security.spi..");
}

package io.github.smiskinext.tenant.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import io.github.smiskinext.shared.architecture.CleanArchitectureTest;

@AnalyzeClasses(
        packages = "io.github.smiskinext.tenant",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest extends CleanArchitectureTest {

    /**
     * Override the parent rule to allow presentation classes to depend on shared infrastructure
     * utilities (ResultResponder, TenantContext) which are designed as cross-cutting concerns for the
     * presentation layer.
     */
    @ArchTest
    static final ArchRule presentation_must_not_depend_on_infrastructure = noClasses()
            .that()
            .resideInAPackage("..presentation..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..tenant.infrastructure..")
            .allowEmptyShould(true)
            .because("Presentation must drive the application through use-case interfaces,"
                    + " never depend on service-specific infrastructure adapters directly");
}

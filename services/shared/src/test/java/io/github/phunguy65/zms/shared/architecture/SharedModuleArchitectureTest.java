package io.github.phunguy65.zms.shared.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Architecture tests for the shared module itself.
 *
 * <p>Enforces that {@code shared.domain} primitives remain framework-agnostic and do not depend on
 * {@code shared.infrastructure} utilities.
 */
@AnalyzeClasses(
        packages = "io.github.phunguy65.zms.shared",
        importOptions = ImportOption.DoNotIncludeTests.class)
class SharedModuleArchitectureTest {

    @ArchTest
    static final ArchRule shared_domain_must_not_depend_on_shared_infrastructure = noClasses()
            .that()
            .resideInAPackage("io.github.phunguy65.zms.shared.domain..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("io.github.phunguy65.zms.shared.infrastructure..")
            .because("shared.domain contains framework-agnostic primitives (AggregateRoot,"
                    + " Result, ValueObject) and must not depend on"
                    + " shared.infrastructure web/cache/logging utilities");

    @ArchTest
    static final ArchRule shared_domain_must_not_depend_on_spring = noClasses()
            .that()
            .resideInAPackage("io.github.phunguy65.zms.shared.domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "jakarta.persistence..")
            .because("shared.domain primitives must be usable without Spring or JPA on the"
                    + " classpath");
}

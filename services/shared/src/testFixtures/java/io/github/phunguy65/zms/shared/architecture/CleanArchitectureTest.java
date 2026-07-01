package io.github.phunguy65.zms.shared.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

/**
 * Abstract base class enforcing Clean Architecture dependency rules for all services.
 *
 * <p>Subclasses must be annotated with {@code @AnalyzeClasses} pointing to the service root
 * package, e.g.:
 *
 * <pre>{@code
 * @AnalyzeClasses(
 *     packages = "io.github.phunguy65.zms.usermanagement",
 *     importOptions = ImportOption.DoNotIncludeTests.class
 * )
 * class ArchitectureTest extends CleanArchitectureTest {}
 * }</pre>
 *
 * <p>Clean Architecture dependency rule:
 *
 * <pre>
 *   presentation ──▶ application ──▶ domain
 *   infrastructure ──────────────────▶ domain
 *   (outer layers depend on inner; inner layers know nothing of outer)
 * </pre>
 */
public abstract class CleanArchitectureTest {

    private static final String SHARED_INFRASTRUCTURE =
            "io.github.phunguy65.zms.shared.infrastructure..";

    /**
     * Predicate that excludes Spring AOT-generated and CGLib proxy classes.
     *
     * <p>Spring generates classes like {@code Foo$$SpringCGLIB$$0} (CGLib proxies) and
     * {@code Foo__TestContext001_BeanDefinitions} (AOT context registrations). These are bytecode
     * artifacts and must not be treated as intentional architectural decisions.
     */
    private static final DescribedPredicate<JavaClass> NOT_SPRING_GENERATED =
            new DescribedPredicate<>("not a Spring AOT or CGLib generated class") {
                @Override
                public boolean test(JavaClass javaClass) {
                    String name = javaClass.getName();
                    return !name.contains("$$")
                            && !name.contains("__TestContext")
                            && !name.contains("__BeanDefinitions")
                            && !name.contains("__BeanFactoryRegistrations")
                            && !name.contains("__ApplicationContextInitializer");
                }
            };

    // ─── Dependency Rules ────────────────────────────────────────────────────

    /**
     * Domain is the innermost circle — it must not depend on any outer layer or framework
     * infrastructure.
     */
    @ArchTest
    static final ArchRule domain_must_not_depend_on_outer_layers = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..application..", "..infrastructure..", "..presentation..")
            .allowEmptyShould(true)
            .because("Domain is the innermost Clean Architecture circle and must not depend"
                    + " on any outer layer");

    /**
     * Domain must not import shared infrastructure utilities — only shared domain primitives are
     * allowed.
     */
    @ArchTest
    static final ArchRule domain_must_not_depend_on_shared_infrastructure = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage(SHARED_INFRASTRUCTURE)
            .allowEmptyShould(true)
            .because("Domain may only use shared domain primitives (AggregateRoot, Result,"
                    + " ValueObject), not shared infrastructure utilities");

    /**
     * Application use cases orchestrate domain logic — they must not reach into infrastructure
     * implementations. Spring AOT-generated classes are excluded from this check.
     */
    @ArchTest
    static final ArchRule application_must_not_depend_on_infrastructure = noClasses()
            .that()
            .resideInAPackage("..application..")
            .and(NOT_SPRING_GENERATED)
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..infrastructure..")
            .allowEmptyShould(true)
            .because("Application layer must depend only on domain ports (interfaces), never"
                    + " on infrastructure implementations");

    /**
     * Application layer must not depend on presentation — use cases are not aware of HTTP/REST
     * concerns. Spring AOT-generated classes are excluded from this check.
     */
    @ArchTest
    static final ArchRule application_must_not_depend_on_presentation = noClasses()
            .that()
            .resideInAPackage("..application..")
            .and(NOT_SPRING_GENERATED)
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..presentation..")
            .allowEmptyShould(true)
            .because("Application layer must not depend on presentation (HTTP/REST) layer");

    // ─── Naming Convention Rules ─────────────────────────────────────────────

    /**
     * Use cases live in application.usecase and must be named with the UseCase suffix. Spring
     * AOT/CGLib generated proxy classes are excluded.
     */
    @ArchTest
    static final ArchRule use_cases_must_have_UseCase_suffix = classes()
            .that()
            .resideInAPackage("..application.usecase..")
            .and(NOT_SPRING_GENERATED)
            .should()
            .haveSimpleNameEndingWith("UseCase")
            .allowEmptyShould(true)
            .because("All classes in application.usecase must be named *UseCase");

    /**
     * Persistence adapters in infrastructure.persistence must be named *RepositoryAdapter to make
     * their role explicit.
     */
    @ArchTest
    static final ArchRule persistence_adapters_must_have_RepositoryAdapter_suffix = classes()
            .that()
            .resideInAPackage("..infrastructure.persistence..")
            .and()
            .areAnnotatedWith(Repository.class)
            .should()
            .haveSimpleNameEndingWith("RepositoryAdapter")
            .allowEmptyShould(true)
            .because("Persistence adapters must be named *RepositoryAdapter to distinguish"
                    + " them from JPA repositories");

    /** JPA entity classes must be named *JpaEntity to distinguish them from domain models. */
    @ArchTest
    static final ArchRule jpa_entities_must_have_JpaEntity_suffix = classes()
            .that()
            .resideInAPackage("..infrastructure.persistence..")
            .and()
            .areAnnotatedWith(Entity.class)
            .should()
            .haveSimpleNameEndingWith("JpaEntity")
            .allowEmptyShould(true)
            .because("JPA entities must be named *JpaEntity to distinguish them from domain"
                    + " aggregates and value objects");

    /** REST controllers must be named *Controller. */
    @ArchTest
    static final ArchRule controllers_must_have_Controller_suffix = classes()
            .that()
            .areAnnotatedWith(RestController.class)
            .should()
            .haveSimpleNameEndingWith("Controller")
            .allowEmptyShould(true)
            .because("REST controllers must be named *Controller");

    // ─── Annotation Placement Rules ──────────────────────────────────────────

    /** @Service belongs only in the application layer (use cases). */
    @ArchTest
    static final ArchRule service_annotation_only_in_application = classes()
            .that()
            .areAnnotatedWith(Service.class)
            .should()
            .resideInAPackage("..application..")
            .allowEmptyShould(true)
            .because("@Service marks application use cases; it must not appear in other layers");

    /** @Repository belongs only in infrastructure.persistence. */
    @ArchTest
    static final ArchRule repository_annotation_only_in_infrastructure_persistence = classes()
            .that()
            .areAnnotatedWith(Repository.class)
            .should()
            .resideInAPackage("..infrastructure.persistence..")
            .allowEmptyShould(true)
            .because(
                    "@Repository marks persistence adapters and must live in infrastructure.persistence");

    /** @RestController belongs only in the presentation layer. */
    @ArchTest
    static final ArchRule rest_controller_annotation_only_in_presentation = classes()
            .that()
            .areAnnotatedWith(RestController.class)
            .should()
            .resideInAPackage("..presentation..")
            .allowEmptyShould(true)
            .because(
                    "@RestController marks HTTP entry points and must live in the presentation layer");

    /** @Entity belongs only in infrastructure.persistence — domain models must be persistence-ignorant. */
    @ArchTest
    static final ArchRule entity_annotation_only_in_infrastructure_persistence = classes()
            .that()
            .areAnnotatedWith(Entity.class)
            .should()
            .resideInAPackage("..infrastructure.persistence..")
            .allowEmptyShould(true)
            .because("@Entity is a JPA concern; domain models must be persistence-ignorant."
                    + " JPA entities belong in infrastructure.persistence");

    /**
     * Naming guide for @Document classes: if a @Document class appears in
     * infrastructure.persistence, it should be named *Document to distinguish it from domain
     * models. This is informational only — @Document is permitted in the domain layer.
     */
    @ArchTest
    static final ArchRule document_naming_guide = classes()
            .that()
            .areAnnotatedWith(Document.class)
            .and()
            .resideInAPackage("..infrastructure.persistence..")
            .should()
            .haveSimpleNameEndingWith("Document")
            .allowEmptyShould(true)
            .because("Naming guide: @Document classes in infrastructure.persistence should be named"
                    + " *Document. This is informational; @Document is allowed in domain.");

    /** Domain classes must not carry Spring stereotype annotations. */
    @ArchTest
    static final ArchRule domain_must_not_have_spring_stereotypes = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .beAnnotatedWith(Service.class)
            .orShould()
            .beAnnotatedWith(Repository.class)
            .orShould()
            .beAnnotatedWith(Entity.class)
            .allowEmptyShould(true)
            .because("Domain classes must be framework-agnostic; Spring/JPA annotations"
                    + " must not appear in the domain layer");
}

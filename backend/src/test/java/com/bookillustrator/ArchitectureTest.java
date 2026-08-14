package com.bookillustrator;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces the Clean Architecture layering from docs/architecture.md: dependencies
 * point inward only. This is the actual proof the layering isn't just folder names.
 */
class ArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter().importPackages("com.bookillustrator");

    @Test
    void domainHasNoSpringOrOuterLayerDependencies() {
        // jakarta.persistence is allowed here — the user's explicit call is that
        // domain.entity classes ARE the JPA entities (amends docs/architecture.md
        // §4/§9, see DECISIONS.md). Spring itself and outer layers are still forbidden.
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..infrastructure..",
                        "..interfaces..",
                        "org.springframework..")
                .because("domain must not depend on Spring or outer layers");
        rule.check(classes);
    }

    @Test
    void applicationDoesNotDependOnInfrastructureOrInterfaces() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..infrastructure..",
                        "..interfaces..")
                .because("application depends on domain only, never on outer layers");
        rule.check(classes);
    }

    @Test
    void restControllersDoNotDependOnInfrastructureDirectly() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..interfaces.rest.controller..")
                .should().dependOnClassesThat().resideInAnyPackage("..infrastructure..")
                .because("controllers must go through an application use case, never call infrastructure directly");
        rule.check(classes);
    }
}

package com.bookillustrator;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces the Clean Architecture layering from CLAUDE.md §1.1: dependencies point
 * inward only. This is the actual proof the layering isn't just folder names.
 */
class ArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter().importPackages("com.bookillustrator");

    @Test
    void domainHasNoFrameworkOrOuterLayerDependencies() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..infrastructure..",
                        "..controller..",
                        "org.springframework..",
                        "jakarta.persistence..")
                .because("domain must stay plain Java — no framework, no outer layers");
        rule.check(classes);
    }

    @Test
    void applicationDoesNotDependOnInfrastructureOrController() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..infrastructure..",
                        "..controller..")
                .because("application depends on domain only, never on outer layers");
        rule.check(classes);
    }

    @Test
    void controllerDoesNotDependOnInfrastructureDirectly() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..controller..")
                .should().dependOnClassesThat().resideInAnyPackage("..infrastructure..")
                .because("controller must go through an application use case, never call infrastructure directly");
        rule.check(classes);
    }
}

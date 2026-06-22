package com.cobre.eventnotifications.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;

/**
 * Guards the hexagonal boundaries. The domain and application layers form the framework-free core and
 * must not reach out to Spring, Jackson, or the infrastructure adapters.
 */
@AnalyzeClasses(packages = "com.cobre.eventnotifications", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    @ArchTest
    static final ArchRule core_must_not_depend_on_frameworks_or_infrastructure = ArchRuleDefinition.noClasses()
            .that()
            .resideInAnyPackage("..domain..", "..application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "com.fasterxml.jackson..", "..infrastructure..")
            .as("domain and application must not depend on Spring, Jackson, or infrastructure")
            .because("the hexagonal core must stay independent of frameworks and outbound adapters")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule domain_must_not_depend_on_application = ArchRuleDefinition.noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..application..")
            .as("domain must not depend on application")
            .because("the domain is the innermost layer of the hexagon")
            .allowEmptyShould(true);
}

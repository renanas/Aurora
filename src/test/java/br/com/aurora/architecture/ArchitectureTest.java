package br.com.aurora.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Sem estes testes, a divisão em pastas seria só uma convenção.
 * Camadas dentro de cada módulo: web -> application -> domain; persistence -> domain.
 */
@AnalyzeClasses(packages = "br.com.aurora", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domainIsIndependentOfSpringAndOtherLayers =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework..",
                            "br.com.aurora..web..",
                            "br.com.aurora..application..",
                            "br.com.aurora..persistence..");

    @ArchTest
    static final ArchRule webDoesNotTouchPersistenceOrDomain = noClasses()
            .that().resideInAPackage("..web..")
            .should().dependOnClassesThat().resideInAnyPackage("..persistence..", "..domain..");

    @ArchTest
    static final ArchRule applicationDoesNotKnowWeb = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..web..");

    @ArchTest
    static final ArchRule persistenceDoesNotKnowUpperLayers = noClasses()
            .that().resideInAPackage("..persistence..")
            .should().dependOnClassesThat().resideInAnyPackage("..application..", "..web..");
}

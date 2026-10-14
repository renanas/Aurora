package br.com.aurora.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Sem estes testes, a divisão em pastas seria só uma convenção.
 *
 * Camadas dentro de cada módulo: web -> application -> domain; persistence -> domain.
 * Entre módulos: scheduling fala com patient só por patient.api; patient nunca conhece scheduling.
 *
 * Atenção: os padrões das camadas levam o prefixo "br.com.aurora", senão "..persistence.." casaria
 * também com jakarta.persistence (as anotações JPA das entidades) e daria falso positivo.
 */
@AnalyzeClasses(packages = "br.com.aurora", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    // ---------- camadas dentro de cada módulo ----------

    @ArchTest
    static final ArchRule domainIsIndependentOfSpringAndOtherLayers = noClasses()
            .that().resideInAPackage("br.com.aurora..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "br.com.aurora..web..",
                    "br.com.aurora..application..",
                    "br.com.aurora..persistence..");

    @ArchTest
    static final ArchRule webDoesNotTouchPersistenceOrDomain = noClasses()
            .that().resideInAPackage("br.com.aurora..web..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "br.com.aurora..persistence..", "br.com.aurora..domain..");

    @ArchTest
    static final ArchRule applicationDoesNotKnowWeb = noClasses()
            .that().resideInAPackage("br.com.aurora..application..")
            .should().dependOnClassesThat().resideInAPackage("br.com.aurora..web..");

    @ArchTest
    static final ArchRule persistenceDoesNotKnowUpperLayers = noClasses()
            .that().resideInAPackage("br.com.aurora..persistence..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "br.com.aurora..application..", "br.com.aurora..web..");

    // ---------- entre módulos ----------

    @ArchTest
    static final ArchRule schedulingTalksToPatientOnlyThroughItsApi = noClasses()
            .that().resideInAPackage("br.com.aurora.scheduling..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "br.com.aurora.patient.domain..",
                    "br.com.aurora.patient.application..",
                    "br.com.aurora.patient.persistence..",
                    "br.com.aurora.patient.web..");

    @ArchTest
    static final ArchRule patientDoesNotKnowScheduling = noClasses()
            .that().resideInAPackage("br.com.aurora.patient..")
            .should().dependOnClassesThat().resideInAPackage("br.com.aurora.scheduling..");

    @ArchTest
    static final ArchRule patientApiIsASelfContainedContract = noClasses()
            .that().resideInAPackage("br.com.aurora.patient.api..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "br.com.aurora.patient.domain..",
                    "br.com.aurora.patient.application..",
                    "br.com.aurora.patient.persistence..",
                    "br.com.aurora.patient.web..",
                    "br.com.aurora.scheduling..");

    @ArchTest
    static final ArchRule sharedDoesNotKnowModules = noClasses()
            .that().resideInAPackage("br.com.aurora.shared..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "br.com.aurora.patient..", "br.com.aurora.scheduling..");
}

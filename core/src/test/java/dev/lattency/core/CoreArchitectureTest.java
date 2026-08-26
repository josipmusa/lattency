package dev.lattency.core;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "dev.lattency.core", importOptions = ImportOption.DoNotIncludeTests.class)
class CoreArchitectureTest {
    @ArchTest
    static final ArchRule CORE_HAS_NO_INTELLIJ_DEPENDENCIES = noClasses()
            .should().dependOnClassesThat().resideInAnyPackage("com.intellij..")
            .because("the core module must remain usable without IntelliJ");
}

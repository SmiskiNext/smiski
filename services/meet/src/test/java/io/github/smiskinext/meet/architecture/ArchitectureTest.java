package io.github.smiskinext.meet.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;

import io.github.smiskinext.shared.architecture.CleanArchitectureTest;

@AnalyzeClasses(
        packages = "io.github.smiskinext.meet",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest extends CleanArchitectureTest {}

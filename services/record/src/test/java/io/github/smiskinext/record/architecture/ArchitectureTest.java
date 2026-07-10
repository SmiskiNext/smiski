package io.github.smiskinext.record.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;

import io.github.smiskinext.shared.architecture.CleanArchitectureTest;

@AnalyzeClasses(
        packages = "io.github.smiskinext.record",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest extends CleanArchitectureTest {}

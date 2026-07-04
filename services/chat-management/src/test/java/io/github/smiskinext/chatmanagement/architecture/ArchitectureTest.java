package io.github.smiskinext.chatmanagement.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import io.github.phunguy65.zms.shared.architecture.CleanArchitectureTest;

@AnalyzeClasses(
        packages = "io.github.phunguy65.zms.chatmanagement",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest extends CleanArchitectureTest {}

package com.mango.products;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.Architectures.onionArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.mango.products", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

	@ArchTest
	static final ArchRule hexagonalLayers = onionArchitecture()
			.domainModels("..domain..")
			.applicationServices("..application..")
			.adapter("web", "..adapters.in.web..")
			.adapter("persistence", "..adapters.out.mongo..")
			.withOptionalLayers(true);

	@ArchTest
	static final ArchRule domainIsPlainJava = classes().that().resideInAPackage("..domain..")
			.should().onlyDependOnClassesThat().resideInAnyPackage("..domain..", "java..");

}

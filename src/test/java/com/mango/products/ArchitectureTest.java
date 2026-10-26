package com.mango.products;

import static com.tngtech.archunit.base.DescribedPredicate.alwaysTrue;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
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
			.adapter("cache", "..adapters.out.redis..")
			.adapter("no-cache", "..adapters.out.nocache..")
			.adapter("near-cache", "..adapters.out.nearcache..")
			.withOptionalLayers(true)
			// config is the composition root: the one place allowed to pick adapters.
			.ignoreDependency(resideInAPackage("..config.."), alwaysTrue());

	@ArchTest
	static final ArchRule domainIsPlainJava = classes().that().resideInAPackage("..domain..")
			.should().onlyDependOnClassesThat().resideInAnyPackage("..domain..", "java..");

}

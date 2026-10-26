package com.mango.products.functional;

import io.karatelabs.junit6.Karate;

class FunctionalTest {

	@Karate.Test
	Karate functionalSuite() {
		return Karate.run("classpath:functional")
				.tags("~@ignore")
				.outputDir(System.getProperty("karate.output.dir", "build/karate-reports"));
	}

}

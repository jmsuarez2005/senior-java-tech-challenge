package com.mango.products;

import static io.restassured.RestAssured.get;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class ProductsApplicationIT {

	@Test
	void healthIsUpWithMongo() {
		get("/actuator/health").then().statusCode(200).body("status", equalTo("UP"));
	}

	@Test
	void nothingElseIsExposedUnderActuator() {
		get("/actuator/env").then().statusCode(404);
	}

}

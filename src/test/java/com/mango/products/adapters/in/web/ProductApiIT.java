package com.mango.products.adapters.in.web;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.config.JsonConfig;
import io.restassured.http.ContentType;
import io.restassured.path.json.config.JsonPathConfig.NumberReturnType;
import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;

@QuarkusTest
class ProductApiIT {

	private static final String PRODUCT = """
			{"name": "Zapatillas deportivas", "description": "Modelo 2025 edición limitada"}""";

	@BeforeAll
	static void readNumbersAsDoubles() {
		RestAssured.config = RestAssured.config().jsonConfig(JsonConfig.jsonConfig().numberReturnType(NumberReturnType.DOUBLE));
	}

	@Test
	void createsProduct() {
		json().body(PRODUCT).post("/products").then()
				.statusCode(201)
				.header("Location", matchesPattern(".*/products/[0-9a-f]{24}"))
				.body("id", notNullValue())
				.body("name", equalTo("Zapatillas deportivas"))
				.body("description", equalTo("Modelo 2025 edición limitada"));
	}

	@Test
	void getsProduct() {
		String id = createProduct();

		given().get("/products/{id}", id).then()
				.statusCode(200)
				.body("id", equalTo(id))
				.body("name", equalTo("Zapatillas deportivas"));
	}

	@Test
	void addsPrice() {
		String id = createProduct();

		addPrice(id, Map.of("value", 99.99, "initDate", "2024-01-01", "endDate", "2024-06-30")).then()
				.statusCode(201)
				.body("value", equalTo(99.99))
				.body("initDate", equalTo("2024-01-01"))
				.body("endDate", equalTo("2024-06-30"));
	}

	@ParameterizedTest(name = "{0} -> {1}")
	@CsvSource({ "2024-01-01, 99.99", "2024-04-15, 99.99", "2024-06-30, 99.99", "2025-03-01, 199.99" })
	void returnsPriceInForceIncludingBothEnds(String date, double expected) {
		String id = createProductWithStatementPrices();

		given().get("/products/{id}/prices?date={date}", id, date).then()
				.statusCode(200)
				.body("$", equalTo(Map.of("value", expected)));
	}

	@Test
	void singularPriceAliasReturnsTheSamePrice() {
		String id = createProductWithStatementPrices();

		given().get("/products/{id}/price?date=2024-04-15", id).then()
				.statusCode(200)
				.body("$", equalTo(Map.of("value", 99.99)));
	}

	@Test
	void returnsNotFoundWhenNoPriceIsInForce() {
		String id = createProductWithStatementPrices();

		expectProblem(given().get("/products/{id}/prices?date=2024-07-01", id).then(), 404);
	}

	@Test
	void returnsHistoryOrderedByInitDate() {
		String id = createProduct();
		addPrice(id, Map.of("value", 199.99, "initDate", "2025-01-01", "endDate", "2025-06-30")).then().statusCode(201);
		addPrice(id, Map.of("value", 99.99, "initDate", "2024-01-01", "endDate", "2024-06-30")).then().statusCode(201);

		given().get("/products/{id}/prices", id).then()
				.statusCode(200)
				.body("$", equalTo(Map.of(
						"name", "Zapatillas deportivas",
						"description", "Modelo 2025 edición limitada",
						"prices", List.of(
								Map.of("value", 99.99, "initDate", "2024-01-01", "endDate", "2024-06-30"),
								Map.of("value", 199.99, "initDate", "2025-01-01", "endDate", "2025-06-30")))));
	}

	@Test
	void readsSeeANewPriceRightAfterItIsAdded() {
		String id = createProductWithStatementPrices();
		given().get("/products/{id}/prices", id).then().statusCode(200).body("prices", hasSize(2));

		addPrice(id, Map.of("value", 149.99, "initDate", "2024-07-01", "endDate", "2024-12-31")).then().statusCode(201);

		given().get("/products/{id}/prices", id).then().statusCode(200).body("prices", hasSize(3));
		given().get("/products/{id}/price?date=2024-10-01", id).then().statusCode(200).body("value", equalTo(149.99));
	}

	@Test
	void historyShowsOpenEndAsNull() {
		String id = createProduct();
		addPrice(id, Map.of("value", 99.99, "initDate", "2024-01-01")).then().statusCode(201);

		given().get("/products/{id}/prices", id).then()
				.statusCode(200)
				.body("prices[0]", hasKey("endDate"))
				.body("prices[0].endDate", nullValue());
	}

	@Test
	void rejectsOverlappingPriceWithConflict() {
		String id = createProductWithStatementPrices();

		expectProblem(addPrice(id, Map.of("value", 79.99, "initDate", "2024-06-30", "endDate", "2024-12-31")).then(), 409);
	}

	@Test
	void keepsOneTimelinePerCurrency() {
		String id = createProductWithStatementPrices();

		addPrice(id, Map.of("value", 109.99, "currency", "USD", "initDate", "2024-01-01", "endDate", "2024-06-30"))
				.then().statusCode(201);

		given().get("/products/{id}/price?date=2024-04-15&currency=USD", id).then().statusCode(200).body("value", equalTo(109.99));
		given().get("/products/{id}/price?date=2024-04-15", id).then().statusCode(200).body("value", equalTo(99.99));
	}

	@ParameterizedTest(name = "{0}")
	@CsvSource(delimiter = '|', textBlock = """
			end before init       | {"value": 10, "initDate": "2024-06-30", "endDate": "2024-01-01"} | endDate
			end equals init       | {"value": 10, "initDate": "2024-06-30", "endDate": "2024-06-30"} | endDate
			too many decimals     | {"value": 9.999, "initDate": "2024-01-01"}                       | value
			no decimals in JPY    | {"value": 100.5, "currency": "JPY", "initDate": "2024-01-01"}    | value
			negative value        | {"value": -1, "initDate": "2024-01-01"}                          | value
			missing value         | {"initDate": "2024-01-01"}                                       | value
			missing init date     | {"value": 10}                                                    | initDate
			huge exponent         | {"value": 1e10000000, "initDate": "2024-01-01"}                  | value
			""")
	void rejectsInvalidPrice(String scenario, String body, String field) {
		String id = createProduct();

		expectProblem(json().body(body).post("/products/{id}/prices", id).then(), 400)
				.body("errors[0].field", equalTo(field));
	}

	@Test
	void reportsAllViolationsAndStoresNothing() {
		String id = createProduct();

		expectProblem(addPrice(id, Map.of("value", 9.999, "initDate", "2024-06-30", "endDate", "2024-01-01")).then(), 400)
				.body("errors.field", containsInAnyOrder("value", "endDate"));

		given().get("/products/{id}/prices", id).then().statusCode(200).body("prices", hasSize(0));
	}

	@Test
	void rejectsMalformedDate() {
		String id = createProduct();

		expectProblem(given().get("/products/{id}/prices?date=2024-13-01", id).then(), 400)
				.body("errors.field", contains("date"));
	}

	@Test
	void rejectsProductWithoutName() {
		expectProblem(json().body(Map.of("name", " ")).post("/products").then(), 400)
				.body("errors[0].field", equalTo("name"));
	}

	@Test
	void returnsNotFoundForUnknownProduct() {
		String unknown = "65f0c0ffee0000000000beef";

		expectProblem(given().get("/products/{id}", unknown).then(), 404);
		expectProblem(given().get("/products/{id}/prices", unknown).then(), 404);
		expectProblem(given().get("/products/{id}/prices?date=2024-01-01", unknown).then(), 404);
		expectProblem(addPrice(unknown, Map.of("value", 10, "initDate", "2024-01-01")).then(), 404);
	}

	private static String createProduct() {
		return json().body(PRODUCT).post("/products").then().statusCode(201).extract().path("id");
	}

	private static String createProductWithStatementPrices() {
		String id = createProduct();
		addPrice(id, Map.of("value", 99.99, "initDate", "2024-01-01", "endDate", "2024-06-30")).then().statusCode(201);
		addPrice(id, Map.of("value", 199.99, "initDate", "2025-01-01", "endDate", "2025-06-30")).then().statusCode(201);
		return id;
	}

	private static Response addPrice(String id, Map<String, ?> body) {
		return json().body(body).post("/products/{id}/prices", id);
	}

	private static RequestSpecification json() {
		return given().contentType(ContentType.JSON);
	}

	private static ValidatableResponse expectProblem(ValidatableResponse response, int status) {
		return response.statusCode(status)
				.contentType("application/problem+json")
				.body("status", equalTo(status));
	}

}

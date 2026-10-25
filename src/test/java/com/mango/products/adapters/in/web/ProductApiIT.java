package com.mango.products.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec;

import com.mango.products.MongoTestcontainersConfiguration;
import com.mango.products.RedisTestcontainersConfiguration;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ActiveProfiles("test")
@Import({ MongoTestcontainersConfiguration.class, RedisTestcontainersConfiguration.class })
class ProductApiIT {

	@Autowired
	RestTestClient client;

	@Test
	void createsProduct() {
		client.post().uri("/products")
				.body(Map.of("name", "Zapatillas deportivas", "description", "Modelo 2025 edición limitada"))
				.exchange()
				.expectStatus().isCreated()
				.expectHeader().value("Location", location -> assertThat(location).matches("/products/[0-9a-f]{24}"))
				.expectBody()
				.jsonPath("$.id").isNotEmpty()
				.jsonPath("$.name").isEqualTo("Zapatillas deportivas")
				.jsonPath("$.description").isEqualTo("Modelo 2025 edición limitada");
	}

	@Test
	void getsProduct() {
		String id = createProduct();

		client.get().uri("/products/{id}", id).exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.id").isEqualTo(id)
				.jsonPath("$.name").isEqualTo("Zapatillas deportivas");
	}

	@Test
	void addsPrice() {
		String id = createProduct();

		addPrice(id, Map.of("value", 99.99, "initDate", "2024-01-01", "endDate", "2024-06-30"))
				.expectStatus().isCreated()
				.expectBody()
				.jsonPath("$.value").isEqualTo(99.99)
				.jsonPath("$.initDate").isEqualTo("2024-01-01")
				.jsonPath("$.endDate").isEqualTo("2024-06-30");
	}

	@ParameterizedTest(name = "{0} -> {1}")
	@CsvSource({ "2024-01-01, 99.99", "2024-04-15, 99.99", "2024-06-30, 99.99", "2025-03-01, 199.99" })
	void returnsPriceInForceIncludingBothEnds(String date, double expected) {
		String id = createProductWithStatementPrices();

		client.get().uri("/products/{id}/prices?date={date}", id, date).exchange()
				.expectStatus().isOk()
				.expectBody().json("{\"value\": " + expected + "}", JsonCompareMode.STRICT);
	}

	@Test
	void singularPriceAliasReturnsTheSamePrice() {
		String id = createProductWithStatementPrices();

		client.get().uri("/products/{id}/price?date=2024-04-15", id).exchange()
				.expectStatus().isOk()
				.expectBody().json("{\"value\": 99.99}", JsonCompareMode.STRICT);
	}

	@Test
	void returnsNotFoundWhenNoPriceIsInForce() {
		String id = createProductWithStatementPrices();

		expectProblem(client.get().uri("/products/{id}/prices?date=2024-07-01", id).exchange(), 404);
	}

	@Test
	void returnsHistoryOrderedByInitDate() {
		String id = createProduct();
		addPrice(id, Map.of("value", 199.99, "initDate", "2025-01-01", "endDate", "2025-06-30")).expectStatus().isCreated();
		addPrice(id, Map.of("value", 99.99, "initDate", "2024-01-01", "endDate", "2024-06-30")).expectStatus().isCreated();

		client.get().uri("/products/{id}/prices", id).exchange()
				.expectStatus().isOk()
				.expectBody().json("""
						{
						  "name": "Zapatillas deportivas",
						  "description": "Modelo 2025 edición limitada",
						  "prices": [
						    { "value": 99.99, "initDate": "2024-01-01", "endDate": "2024-06-30" },
						    { "value": 199.99, "initDate": "2025-01-01", "endDate": "2025-06-30" }
						  ]
						}
						""", JsonCompareMode.STRICT);
	}

	@Test
	void readsSeeANewPriceRightAfterItIsAdded() {
		String id = createProductWithStatementPrices();
		client.get().uri("/products/{id}/prices", id).exchange()
				.expectStatus().isOk()
				.expectBody().jsonPath("$.prices.length()").isEqualTo(2);

		addPrice(id, Map.of("value", 149.99, "initDate", "2024-07-01", "endDate", "2024-12-31")).expectStatus().isCreated();

		client.get().uri("/products/{id}/prices", id).exchange()
				.expectStatus().isOk()
				.expectBody().jsonPath("$.prices.length()").isEqualTo(3);
		client.get().uri("/products/{id}/price?date=2024-10-01", id).exchange()
				.expectStatus().isOk()
				.expectBody().jsonPath("$.value").isEqualTo(149.99);
	}

	@Test
	void historyShowsOpenEndAsNull() {
		String id = createProduct();
		addPrice(id, Map.of("value", 99.99, "initDate", "2024-01-01")).expectStatus().isCreated();

		client.get().uri("/products/{id}/prices", id).exchange()
				.expectStatus().isOk()
				.expectBody().json("""
						{ "prices": [ { "value": 99.99, "initDate": "2024-01-01", "endDate": null } ] }
						""");
	}

	@Test
	void rejectsOverlappingPriceWithConflict() {
		String id = createProductWithStatementPrices();

		expectProblem(addPrice(id, Map.of("value", 79.99, "initDate", "2024-06-30", "endDate", "2024-12-31")), 409);
	}

	@Test
	void keepsOneTimelinePerCurrency() {
		String id = createProductWithStatementPrices();

		addPrice(id, Map.of("value", 109.99, "currency", "USD", "initDate", "2024-01-01", "endDate", "2024-06-30"))
				.expectStatus().isCreated();

		client.get().uri("/products/{id}/price?date=2024-04-15&currency=USD", id).exchange()
				.expectStatus().isOk()
				.expectBody().jsonPath("$.value").isEqualTo(109.99);
		client.get().uri("/products/{id}/price?date=2024-04-15", id).exchange()
				.expectStatus().isOk()
				.expectBody().jsonPath("$.value").isEqualTo(99.99);
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
			""")
	void rejectsInvalidPrice(String scenario, String body, String field) {
		String id = createProduct();

		ResponseSpec response = client.post().uri("/products/{id}/prices", id)
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.exchange();

		expectProblem(response, 400).jsonPath("$.errors[0].field").isEqualTo(field);
	}

	@Test
	void reportsAllViolationsAndStoresNothing() {
		String id = createProduct();

		expectProblem(addPrice(id, Map.of("value", 9.999, "initDate", "2024-06-30", "endDate", "2024-01-01")), 400)
				.jsonPath("$.errors.length()").isEqualTo(2)
				.jsonPath("$.errors[?(@.field == 'value')]").exists()
				.jsonPath("$.errors[?(@.field == 'endDate')]").exists();

		client.get().uri("/products/{id}/prices", id).exchange()
				.expectStatus().isOk()
				.expectBody().jsonPath("$.prices").isEmpty();
	}

	@Test
	void rejectsMalformedDate() {
		String id = createProduct();

		expectProblem(client.get().uri("/products/{id}/prices?date=2024-13-01", id).exchange(), 400);
	}

	@Test
	void rejectsProductWithoutName() {
		ResponseSpec response = client.post().uri("/products").body(Map.of("name", " ")).exchange();

		expectProblem(response, 400).jsonPath("$.errors[0].field").isEqualTo("name");
	}

	@Test
	void returnsNotFoundForUnknownProduct() {
		String unknown = "65f0c0ffee0000000000beef";

		expectProblem(client.get().uri("/products/{id}", unknown).exchange(), 404);
		expectProblem(client.get().uri("/products/{id}/prices", unknown).exchange(), 404);
		expectProblem(client.get().uri("/products/{id}/prices?date=2024-01-01", unknown).exchange(), 404);
		expectProblem(addPrice(unknown, Map.of("value", 10, "initDate", "2024-01-01")), 404);
	}

	private String createProduct() {
		return client.post().uri("/products")
				.body(Map.of("name", "Zapatillas deportivas", "description", "Modelo 2025 edición limitada"))
				.exchange()
				.expectStatus().isCreated()
				.expectBody(Map.class)
				.returnResult()
				.getResponseBody()
				.get("id")
				.toString();
	}

	private String createProductWithStatementPrices() {
		String id = createProduct();
		addPrice(id, Map.of("value", 99.99, "initDate", "2024-01-01", "endDate", "2024-06-30")).expectStatus().isCreated();
		addPrice(id, Map.of("value", 199.99, "initDate", "2025-01-01", "endDate", "2025-06-30")).expectStatus().isCreated();
		return id;
	}

	private ResponseSpec addPrice(String id, Map<String, ?> body) {
		return client.post().uri("/products/{id}/prices", id).body(body).exchange();
	}

	private static RestTestClient.BodyContentSpec expectProblem(ResponseSpec response, int status) {
		return response.expectStatus().isEqualTo(status)
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody().jsonPath("$.status").isEqualTo(status);
	}

}

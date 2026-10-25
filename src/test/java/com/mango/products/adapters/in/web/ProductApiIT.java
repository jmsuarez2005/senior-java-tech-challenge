package com.mango.products.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

import com.mango.products.MongoTestcontainersConfiguration;
import com.mango.products.ThirdPartyBlockingAllowList;

import reactor.blockhound.BlockHound;
import reactor.blockhound.BlockingOperationError;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
@Import(MongoTestcontainersConfiguration.class)
class ProductApiIT {

	@Autowired
	WebTestClient client;

	@BeforeAll
	static void installBlockHound() {
		BlockHound.install(new ThirdPartyBlockingAllowList());
	}

	@Test
	void blockHoundIsActive() {
		Mono<Long> blocking = Mono.delay(Duration.ofMillis(1)).doOnNext(tick -> sleep());

		StepVerifier.create(blocking).expectError(BlockingOperationError.class).verify();
	}

	@Test
	void createsProduct() {
		client.post().uri("/products")
				.bodyValue(Map.of("name", "Zapatillas deportivas", "description", "Modelo 2025 edición limitada"))
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
				.expectBody().json("{\"value\": " + expected + "}", true);
	}

	@Test
	void singularPriceAliasReturnsTheSamePrice() {
		String id = createProductWithStatementPrices();

		client.get().uri("/products/{id}/price?date=2024-04-15", id).exchange()
				.expectStatus().isOk()
				.expectBody().json("{\"value\": 99.99}", true);
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
						""", true);
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
				.bodyValue(body)
				.exchange();

		expectProblem(response, 400).jsonPath("$.errors[0].field").isEqualTo(field);
	}

	@Test
	void rejectsMalformedDate() {
		String id = createProduct();

		expectProblem(client.get().uri("/products/{id}/prices?date=2024-13-01", id).exchange(), 400);
	}

	@Test
	void rejectsProductWithoutName() {
		ResponseSpec response = client.post().uri("/products").bodyValue(Map.of("name", " ")).exchange();

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
				.bodyValue(Map.of("name", "Zapatillas deportivas", "description", "Modelo 2025 edición limitada"))
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
		return client.post().uri("/products/{id}/prices", id).bodyValue(body).exchange();
	}

	private static WebTestClient.BodyContentSpec expectProblem(ResponseSpec response, int status) {
		return response.expectStatus().isEqualTo(status)
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody().jsonPath("$.status").isEqualTo(status);
	}

	private static void sleep() {
		try {
			Thread.sleep(10);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

}

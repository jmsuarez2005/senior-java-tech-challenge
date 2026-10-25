package com.mango.products;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ActiveProfiles("test")
@Import(MongoTestcontainersConfiguration.class)
@TestPropertySource(properties = "products.cache.enabled=false")
class ProductsApplicationIT {

	@Autowired
	RestTestClient client;

	@Test
	void healthIsUpWithMongo() {
		client.get().uri("/actuator/health").exchange()
				.expectStatus().isOk()
				.expectBody().jsonPath("$.status").isEqualTo("UP");
	}

	@Test
	void onlyHealthIsExposed() {
		client.get().uri("/actuator/env").exchange().expectStatus().isNotFound();
	}

}

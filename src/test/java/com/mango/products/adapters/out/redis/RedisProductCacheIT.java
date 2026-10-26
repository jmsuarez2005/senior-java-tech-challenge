package com.mango.products.adapters.out.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mango.products.domain.DateRange;
import com.mango.products.domain.Money;
import com.mango.products.domain.Price;
import com.mango.products.domain.Product;
import com.mango.products.domain.ProductId;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
class RedisProductCacheIT {

	private static final Currency EUR = Currency.getInstance("EUR");

	@Inject
	ReactiveRedisDataSource redis;

	@Inject
	ObjectMapper json;

	RedisProductCache cache;

	@BeforeEach
	void setUp() {
		cache = new RedisProductCache(redis, json, Duration.ofMinutes(1), new SimpleMeterRegistry());
	}

	@Test
	void missIsEmpty() {
		assertThat(get(new ProductId("missing"))).isNull();
	}

	@Test
	void roundTripsAProductWithAnOpenEndedPrice() {
		Product product = product("p-round-trip", 2, price("99.99", "2024-01-01", "2024-06-30"),
				price("129.99", "2024-07-01", null));

		put(product);

		assertThat(get(product.id())).isEqualTo(product);
	}

	@Test
	void newerVersionReplacesTheCachedOne() {
		Product v1 = product("p-newer", 1, price("99.99", "2024-01-01", null));
		Product v2 = product("p-newer", 2, price("99.99", "2024-01-01", "2024-06-30"), price("129.99", "2024-07-01", null));

		put(v1);
		put(v2);

		assertThat(get(v2.id())).isEqualTo(v2);
	}

	@Test
	void olderVersionNeverOverwritesANewerOne() {
		Product v1 = product("p-older", 1, price("99.99", "2024-01-01", null));
		Product v2 = product("p-older", 2, price("99.99", "2024-01-01", "2024-06-30"), price("129.99", "2024-07-01", null));

		put(v2);
		put(v1);

		assertThat(get(v2.id())).isEqualTo(v2);
	}

	@Test
	void aFailedWriteRemovesTheEntryInsteadOfLeavingAStaleOne() {
		Product product = product("p-unwritable", 3, price("99.99", "2024-01-01", null));
		redis.value(String.class).set("product:p-unwritable", "not json").await().atMost(Duration.ofSeconds(5));

		put(product);

		assertThat(redis.key(String.class).exists("product:p-unwritable").await().atMost(Duration.ofSeconds(5))).isFalse();
		assertThat(get(product.id())).isNull();
	}

	private Product get(ProductId id) {
		return cache.get(id).await().atMost(Duration.ofSeconds(5));
	}

	private void put(Product product) {
		cache.put(product).await().atMost(Duration.ofSeconds(5));
	}

	private static Product product(String id, long version, Price... prices) {
		return new Product(new ProductId(id), "Sneakers", "2025 limited edition", List.of(prices), version);
	}

	private static Price price(String value, String initDate, String endDate) {
		return new Price(Money.of(new BigDecimal(value), EUR),
				new DateRange(LocalDate.parse(initDate), endDate == null ? null : LocalDate.parse(endDate)));
	}

}

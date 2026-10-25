package com.mango.products.adapters.out.redis;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

import com.mango.products.RedisTestcontainersConfiguration;
import com.mango.products.domain.DateRange;
import com.mango.products.domain.Money;
import com.mango.products.domain.Price;
import com.mango.products.domain.Product;
import com.mango.products.domain.ProductId;

import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

@DataRedisTest
@Import(RedisTestcontainersConfiguration.class)
class RedisProductCacheIT {

	private static final Currency EUR = Currency.getInstance("EUR");

	@Autowired
	ReactiveStringRedisTemplate redis;

	RedisProductCache cache;

	@BeforeEach
	void setUp() {
		cache = new RedisProductCache(redis, JsonMapper.builder().build(), Duration.ofMinutes(1));
	}

	@Test
	void missIsEmpty() {
		StepVerifier.create(cache.get(new ProductId("missing"))).verifyComplete();
	}

	@Test
	void roundTripsAProductWithAnOpenEndedPrice() {
		Product product = product("p-round-trip", 2, price("99.99", "2024-01-01", "2024-06-30"),
				price("129.99", "2024-07-01", null));

		StepVerifier.create(cache.put(product).then(cache.get(product.id())))
				.expectNext(product)
				.verifyComplete();
	}

	@Test
	void newerVersionReplacesTheCachedOne() {
		Product v1 = product("p-newer", 1, price("99.99", "2024-01-01", null));
		Product v2 = product("p-newer", 2, price("99.99", "2024-01-01", "2024-06-30"), price("129.99", "2024-07-01", null));

		StepVerifier.create(cache.put(v1).then(cache.put(v2)).then(cache.get(v2.id())))
				.expectNext(v2)
				.verifyComplete();
	}

	@Test
	void olderVersionNeverOverwritesANewerOne() {
		Product v1 = product("p-older", 1, price("99.99", "2024-01-01", null));
		Product v2 = product("p-older", 2, price("99.99", "2024-01-01", "2024-06-30"), price("129.99", "2024-07-01", null));

		StepVerifier.create(cache.put(v2).then(cache.put(v1)).then(cache.get(v2.id())))
				.expectNext(v2)
				.verifyComplete();
	}

	private static Product product(String id, long version, Price... prices) {
		return new Product(new ProductId(id), "Sneakers", "2025 limited edition", List.of(prices), version);
	}

	private static Price price(String value, String initDate, String endDate) {
		return new Price(Money.of(new BigDecimal(value), EUR),
				new DateRange(LocalDate.parse(initDate), endDate == null ? null : LocalDate.parse(endDate)));
	}

}

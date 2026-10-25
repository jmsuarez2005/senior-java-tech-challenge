package com.mango.products.domain;

import static com.mango.products.domain.PriceTest.price;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Currency;
import java.util.List;

import org.junit.jupiter.api.Test;

class ProductTest {

	private static final Currency EUR = Currency.getInstance("EUR");

	@Test
	void keepsPricesOrderedByInitDate() {
		Price later = price(19999, EUR, "2025-01-01");
		Price earlier = price(9999, EUR, "2024-01-01");

		Product product = new Product("p1", "Sneakers", null, List.of(later, earlier));

		assertThat(product.prices()).containsExactly(earlier, later);
	}

	@Test
	void requiresName() {
		assertThatThrownBy(() -> new Product("p1", " ", null, List.of()))
				.isInstanceOfSatisfying(DomainValidationException.class, e -> assertThat(e.field()).isEqualTo("name"));
	}

}

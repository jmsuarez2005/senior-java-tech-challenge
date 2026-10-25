package com.mango.products.domain;

import static com.mango.products.domain.PriceTest.price;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Currency;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProductTest {

	private static final Currency EUR = Currency.getInstance("EUR");
	private static final Currency USD = Currency.getInstance("USD");

	private static final Price FIRST_HALF_2024 = price(9999, EUR, "2024-01-01");
	private static final Price FIRST_HALF_2025 = price(19999, EUR, "2025-01-01");

	@Test
	void keepsPricesOrderedByInitDate() {
		Product product = productWith(FIRST_HALF_2025, FIRST_HALF_2024);

		assertThat(product.prices()).containsExactly(FIRST_HALF_2024, FIRST_HALF_2025);
	}

	@Test
	void requiresName() {
		assertThatThrownBy(() -> Product.create(new ProductId("p1"), " ", null))
				.isInstanceOfSatisfying(DomainValidationException.class, e -> assertThat(e.field()).isEqualTo("name"));
	}

	@Test
	void startsAtVersionZeroWithoutPrices() {
		Product product = Product.create(new ProductId("p1"), "Sneakers", null);

		assertThat(product.version()).isZero();
		assertThat(product.prices()).isEmpty();
	}

	@ParameterizedTest(name = "{0} -> {1}")
	@CsvSource({ "2024-01-01, 99.99", "2024-07-01, 99.99", "2025-03-15, 199.99" })
	void findsThePriceInForceOnADate(String date, String expected) {
		Product product = productWith(FIRST_HALF_2024, FIRST_HALF_2025);

		assertThat(product.priceAt(LocalDate.parse(date), EUR))
				.hasValueSatisfying(price -> assertThat(price.money().toDecimal()).hasToString(expected));
	}

	@Test
	void hasNoPriceOutsideItsPeriodsOrInAnotherCurrency() {
		Product product = productWith(FIRST_HALF_2024);

		assertThat(product.priceAt(LocalDate.parse("2024-07-02"), EUR)).isEmpty();
		assertThat(product.priceAt(LocalDate.parse("2024-03-01"), USD)).isEmpty();
	}

	private static Product productWith(Price... prices) {
		return new Product(new ProductId("p1"), "Sneakers", null, List.of(prices), prices.length);
	}

}

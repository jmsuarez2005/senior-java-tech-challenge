package com.mango.products.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Currency;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PriceTest {

	private static final Currency EUR = Currency.getInstance("EUR");
	private static final Currency USD = Currency.getInstance("USD");

	@ParameterizedTest
	@ValueSource(longs = { 0, -1 })
	void rejectsNonPositiveValues(long amountMinor) {
		assertThatThrownBy(() -> price(amountMinor, EUR, "2024-01-01"))
				.isInstanceOfSatisfying(DomainValidationException.class, e -> assertThat(e.field()).isEqualTo("value"));
	}

	@Test
	void samePeriodInAnotherCurrencyDoesNotOverlap() {
		assertThat(price(9999, EUR, "2024-01-01").overlaps(price(10999, USD, "2024-01-01"))).isFalse();
		assertThat(price(9999, EUR, "2024-01-01").overlaps(price(10999, EUR, "2024-01-01"))).isTrue();
	}

	static Price price(long amountMinor, Currency currency, String init) {
		LocalDate initDate = LocalDate.parse(init);
		return new Price(new Money(amountMinor, currency), new DateRange(initDate, initDate.plusMonths(6)));
	}

}

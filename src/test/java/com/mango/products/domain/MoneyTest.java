package com.mango.products.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Currency;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MoneyTest {

	@ParameterizedTest(name = "{0} {1} -> {2} minor units")
	@CsvSource({
			"99.99,  EUR, 9999",
			"99.990, EUR, 9999",
			"100,    EUR, 10000",
			"100,    JPY, 100",
			"1.234,  KWD, 1234",
	})
	void convertsToMinorUnitsUsingTheCurrencyScale(String value, String currency, long expectedMinor) {
		Money money = Money.of(new BigDecimal(value), Currency.getInstance(currency));

		assertThat(money.amountMinor()).isEqualTo(expectedMinor);
	}

	@ParameterizedTest(name = "{0} {1} is rejected")
	@CsvSource({
			"99.999, EUR",
			"100.5,  JPY",
			"1.2345, KWD",
			"99999999999999999999, EUR",
	})
	void rejectsValuesTheCurrencyCannotRepresentInsteadOfRounding(String value, String currency) {
		assertThatThrownBy(() -> Money.of(new BigDecimal(value), Currency.getInstance(currency)))
				.isInstanceOfSatisfying(DomainValidationException.class, e -> assertThat(e.field()).isEqualTo("value"));
	}

	@Test
	void rejectsCurrenciesWithoutMinorUnit() {
		assertThatThrownBy(() -> Money.of(BigDecimal.ONE, Currency.getInstance("XAU")))
				.isInstanceOfSatisfying(DomainValidationException.class, e -> assertThat(e.field()).isEqualTo("currency"));
	}

	@ParameterizedTest
	@CsvSource({ "9999, EUR, 99.99", "100, JPY, 100", "1234, KWD, 1.234" })
	void toDecimalUsesTheCurrencyScale(long minor, String currency, String expected) {
		assertThat(new Money(minor, Currency.getInstance(currency)).toDecimal()).isEqualTo(new BigDecimal(expected));
	}

}

package com.mango.products.adapters.in.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class AddPriceRequestValidationTest {

	private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
	private static final Validator VALIDATOR = FACTORY.getValidator();

	@AfterAll
	static void closeFactory() {
		FACTORY.close();
	}

	@Test
	void acceptsTheStatementExample() {
		assertThat(invalidFields(request("99.99", "2024-01-01", "2024-06-30", null))).isEmpty();
	}

	@Test
	void acceptsOpenEndedPrice() {
		assertThat(invalidFields(request("99.99", "2024-01-01", null, null))).isEmpty();
	}

	@ParameterizedTest(name = "{0}")
	@CsvSource(nullValues = "null", textBlock = """
			end before init,         10,    2024-06-30, 2024-01-01, null, endDate
			end equals init,         10,    2024-06-30, 2024-06-30, null, endDate
			end after sentinel,      10,    2024-01-01, +10000-01-01, null, endDate
			too many EUR decimals,   9.999, 2024-01-01, null,       null, value
			decimals in JPY,         100.5, 2024-01-01, null,       JPY,  value
			currency without minor,  1,     2024-01-01, null,       XAU,  currency
			zero,                    0,     2024-01-01, null,       null, value
			missing value,           null,  2024-01-01, null,       null, value
			missing init date,       10,    null,       null,       null, initDate
			""")
	void reportsTheOffendingField(String scenario, String value, String initDate, String endDate, String currency,
			String field) {
		assertThat(invalidFields(request(value, initDate, endDate, currency))).containsExactly(field);
	}

	@Test
	void reportsEveryViolationAtOnce() {
		assertThat(invalidFields(request("9.999", "2024-06-30", "2024-01-01", null)))
				.containsExactlyInAnyOrder("value", "endDate");
	}

	private static Set<String> invalidFields(AddPriceRequest request) {
		return VALIDATOR.validate(request).stream()
				.map(ConstraintViolation::getPropertyPath)
				.map(Object::toString)
				.collect(Collectors.toSet());
	}

	private static AddPriceRequest request(String value, String initDate, String endDate, String currency) {
		return new AddPriceRequest(
				value == null ? null : new BigDecimal(value),
				initDate == null ? null : LocalDate.parse(initDate),
				endDate == null ? null : LocalDate.parse(endDate),
				currency == null ? null : Currency.getInstance(currency));
	}

}

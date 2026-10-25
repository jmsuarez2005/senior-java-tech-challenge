package com.mango.products.adapters.in.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import com.mango.products.domain.DateRange;
import com.mango.products.domain.Money;
import com.mango.products.domain.Price;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@ValidPrice
public record AddPriceRequest(
		@Schema(examples = "99.99") @NotNull @Positive BigDecimal value,
		@Schema(examples = "2024-01-01") @NotNull LocalDate initDate,
		@Schema(examples = "2024-06-30", description = "Inclusive. Null means until further notice.") LocalDate endDate,
		@Schema(examples = "EUR", description = "ISO 4217 code. Defaults to EUR.") Currency currency) {

	public Currency currencyOrDefault() {
		return currency == null ? ApiDefaults.CURRENCY : currency;
	}

	public Price toPrice() {
		return new Price(Money.of(value, currencyOrDefault()), new DateRange(initDate, endDate));
	}

}

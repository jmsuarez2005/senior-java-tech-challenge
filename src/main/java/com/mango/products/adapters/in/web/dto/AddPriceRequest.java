package com.mango.products.adapters.in.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;

import com.mango.products.domain.DateRange;
import com.mango.products.domain.Money;
import com.mango.products.domain.Price;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AddPriceRequest(
		@Schema(example = "99.99") @NotNull @Positive BigDecimal value,
		@Schema(example = "2024-01-01") @NotNull LocalDate initDate,
		@Schema(example = "2024-06-30", description = "Inclusive. Null means until further notice.") LocalDate endDate,
		@Schema(example = "EUR", description = "ISO 4217 code. Defaults to EUR.") Currency currency) {

	public Price toPrice(Currency defaultCurrency) {
		Money money = Money.of(value, currency == null ? defaultCurrency : currency);
		return new Price(money, new DateRange(initDate, endDate));
	}

}

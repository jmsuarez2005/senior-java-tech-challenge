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

public record ReplacePriceRequest(
		@Schema(examples = "89.99") @NotNull @Positive BigDecimal value,
		@Schema(examples = "2024-03-31", description = "Inclusive. Null means until further notice.") LocalDate endDate) {

	public Price toPrice(Currency currency, LocalDate initDate) {
		return new Price(Money.of(value, currency), new DateRange(initDate, endDate));
	}

}

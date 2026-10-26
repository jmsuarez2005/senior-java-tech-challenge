package com.mango.products.adapters.in.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import com.mango.products.domain.Price;

public record PriceResponse(
		@Schema(description = "Amount in units of the currency", examples = "99.99") BigDecimal value,
		@Schema(description = "First day the price applies, inclusive", examples = "2024-01-01") LocalDate initDate,
		@Schema(description = "Last day the price applies, inclusive; null means until further notice",
				examples = "2024-06-30", nullable = true) LocalDate endDate) {

	public static PriceResponse from(Price price) {
		LocalDate endDate = price.period().isOpenEnded() ? null : price.period().endDate();
		return new PriceResponse(price.money().toDecimal(), price.period().initDate(), endDate);
	}

}

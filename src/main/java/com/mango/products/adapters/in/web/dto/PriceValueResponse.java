package com.mango.products.adapters.in.web.dto;

import java.math.BigDecimal;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import com.mango.products.domain.Price;

public record PriceValueResponse(
		@Schema(description = "Amount in force, in units of the currency", examples = "99.99") BigDecimal value) {

	public static PriceValueResponse from(Price price) {
		return new PriceValueResponse(price.money().toDecimal());
	}

}

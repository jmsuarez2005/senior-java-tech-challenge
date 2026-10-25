package com.mango.products.adapters.in.web.dto;

import java.math.BigDecimal;

import com.mango.products.domain.Price;

public record PriceValueResponse(BigDecimal value) {

	public static PriceValueResponse from(Price price) {
		return new PriceValueResponse(price.money().toDecimal());
	}

}

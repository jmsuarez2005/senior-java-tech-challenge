package com.mango.products.adapters.in.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.mango.products.domain.Price;

public record PriceResponse(BigDecimal value, LocalDate initDate, LocalDate endDate) {

	public static PriceResponse from(Price price) {
		LocalDate endDate = price.period().isOpenEnded() ? null : price.period().endDate();
		return new PriceResponse(price.money().toDecimal(), price.period().initDate(), endDate);
	}

}

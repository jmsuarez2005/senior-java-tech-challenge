package com.mango.products.domain;

import java.time.LocalDate;
import java.util.Currency;

public final class PriceNotFoundException extends DomainException {

	private PriceNotFoundException(String message) {
		super(message);
	}

	public static PriceNotFoundException inForce(ProductId productId, LocalDate date, Currency currency) {
		return new PriceNotFoundException("Product " + productId + " has no " + currency + " price on " + date);
	}

	public static PriceNotFoundException startingOn(ProductId productId, LocalDate initDate, Currency currency) {
		return new PriceNotFoundException("Product " + productId + " has no " + currency + " price starting on " + initDate);
	}

}

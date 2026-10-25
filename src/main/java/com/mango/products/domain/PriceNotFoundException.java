package com.mango.products.domain;

import java.time.LocalDate;
import java.util.Currency;

public final class PriceNotFoundException extends DomainException {

	public PriceNotFoundException(String productId, LocalDate date, Currency currency) {
		super("Product " + productId + " has no " + currency + " price on " + date);
	}

}

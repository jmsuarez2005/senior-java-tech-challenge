package com.mango.products.adapters.in.web.dto;

import java.util.Currency;

public final class ApiDefaults {

	public static final String CURRENCY_CODE = "EUR";
	public static final Currency CURRENCY = Currency.getInstance(CURRENCY_CODE);

	private ApiDefaults() {
	}

}

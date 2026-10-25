package com.mango.products.adapters.in.web;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Currency;

import com.mango.products.domain.DomainValidationException;

// JAX-RS turns an unconvertible query parameter into a 404; parsing here keeps it a 400 with the field name.
final class RequestParams {

	private RequestParams() {
	}

	static LocalDate date(String value) {
		if (value == null) {
			throw new DomainValidationException("date", "date is required");
		}
		try {
			return LocalDate.parse(value);
		}
		catch (DateTimeParseException e) {
			throw new DomainValidationException("date", "must be an ISO date (yyyy-MM-dd)");
		}
	}

	static Currency currency(String code) {
		try {
			return Currency.getInstance(code);
		}
		catch (IllegalArgumentException e) {
			throw new DomainValidationException("currency", "must be an ISO 4217 code");
		}
	}

}

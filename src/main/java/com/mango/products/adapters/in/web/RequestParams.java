package com.mango.products.adapters.in.web;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Currency;

import com.mango.products.adapters.in.web.dto.HistoryQuery;
import com.mango.products.domain.DomainValidationException;

final class RequestParams {

	private RequestParams() {
	}

	static LocalDate date(String value) {
		return requiredDate("date", value);
	}

	static LocalDate requiredDate(String field, String value) {
		if (value == null) {
			throw new DomainValidationException(field, field + " is required");
		}
		return optionalDate(field, value);
	}

	static LocalDate optionalDate(String field, String value) {
		if (value == null) {
			return null;
		}
		try {
			return LocalDate.parse(value);
		}
		catch (DateTimeParseException e) {
			throw new DomainValidationException(field, "must be an ISO date (yyyy-MM-dd)");
		}
	}

	static HistoryQuery historyQuery(String currency, HistoryParams params) {
		LocalDate fromDate = optionalDate("from", params.from());
		LocalDate toDate = optionalDate("to", params.to());
		if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
			throw new DomainValidationException("to", "must not be before from");
		}
		boolean descending = switch (params.sort()) {
			case "initDate,asc", "initDate" -> false;
			case "initDate,desc" -> true;
			default -> throw new DomainValidationException("sort", "must be initDate,asc or initDate,desc");
		};
		if (params.page() < 0) {
			throw new DomainValidationException("page", "must not be negative");
		}
		if (params.size() < 0 || params.size() > HistoryQuery.MAX_PAGE_SIZE) {
			throw new DomainValidationException("size", "must be between 1 and " + HistoryQuery.MAX_PAGE_SIZE);
		}
		return new HistoryQuery(currency(currency), fromDate, toDate, descending, params.page(), params.size());
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

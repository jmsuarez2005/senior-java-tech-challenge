package com.mango.products.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

public record Money(long amountMinor, Currency currency) {

	public Money {
		Objects.requireNonNull(currency, "currency");
		fractionDigits(currency);
	}

	public static Money of(BigDecimal value, Currency currency) {
		Objects.requireNonNull(value, "value");
		try {
			long minor = value.setScale(fractionDigits(currency), RoundingMode.UNNECESSARY)
					.unscaledValue()
					.longValueExact();
			return new Money(minor, currency);
		}
		catch (ArithmeticException e) {
			throw new DomainValidationException("value",
					value.toPlainString() + " is not a valid " + currency + " amount");
		}
	}

	public BigDecimal toDecimal() {
		return BigDecimal.valueOf(amountMinor, fractionDigits(currency));
	}

	private static int fractionDigits(Currency currency) {
		int digits = currency.getDefaultFractionDigits();
		if (digits < 0) {
			throw new DomainValidationException("currency", currency + " is not a monetary currency");
		}
		return digits;
	}

}

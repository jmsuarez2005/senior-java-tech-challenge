package com.mango.products.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Amount in the currency's minor unit (cents for EUR, yen for JPY, fils for KWD).
 * The scale always comes from the currency, never from a fixed factor of 100.
 */
public record Money(long amountMinor, Currency currency) {

	public Money {
		Objects.requireNonNull(currency, "currency");
		fractionDigits(currency);
	}

	/** Rejects, never rounds, a value with more decimals than the currency allows. */
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

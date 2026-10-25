package com.mango.products.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;
import java.util.OptionalLong;

public record Money(long amountMinor, Currency currency) {

	public Money {
		Objects.requireNonNull(currency, "currency");
		fractionDigits(currency);
	}

	public static Money of(BigDecimal value, Currency currency) {
		Objects.requireNonNull(value, "value");
		long minor = toMinorUnits(value, fractionDigits(currency))
				.orElseThrow(() -> new DomainValidationException("value",
						value.toPlainString() + " is not a valid " + currency + " amount"));
		return new Money(minor, currency);
	}

	public static boolean hasMinorUnit(Currency currency) {
		return currency.getDefaultFractionDigits() >= 0;
	}

	// True when the value has no more decimals than the currency allows and fits in a long.
	public static boolean isRepresentable(BigDecimal value, Currency currency) {
		return hasMinorUnit(currency) && toMinorUnits(value, currency.getDefaultFractionDigits()).isPresent();
	}

	public BigDecimal toDecimal() {
		return BigDecimal.valueOf(amountMinor, fractionDigits(currency));
	}

	private static int fractionDigits(Currency currency) {
		if (!hasMinorUnit(currency)) {
			throw new DomainValidationException("currency", currency + " has no minor unit");
		}
		return currency.getDefaultFractionDigits();
	}

	private static OptionalLong toMinorUnits(BigDecimal value, int fractionDigits) {
		try {
			return OptionalLong.of(value.setScale(fractionDigits, RoundingMode.UNNECESSARY).unscaledValue().longValueExact());
		}
		catch (ArithmeticException e) {
			return OptionalLong.empty();
		}
	}

}

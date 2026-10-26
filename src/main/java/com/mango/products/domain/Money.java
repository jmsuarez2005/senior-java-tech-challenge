package com.mango.products.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;
import java.util.OptionalLong;

public record Money(long amountMinor, Currency currency) {

	public static final int MAX_DIGITS = 18;

	public Money {
		Objects.requireNonNull(currency, "currency");
		fractionDigits(currency);
	}

	public static Money of(BigDecimal value, Currency currency) {
		Objects.requireNonNull(value, "value");
		long minor = toMinorUnits(value, fractionDigits(currency))
				.orElseThrow(() -> new DomainValidationException("value",
						value + " is not a valid " + currency + " amount"));
		return new Money(minor, currency);
	}

	public static boolean hasMinorUnit(Currency currency) {
		return currency.getDefaultFractionDigits() >= 0;
	}

	public static boolean isRepresentable(BigDecimal value, Currency currency) {
		return hasMinorUnit(currency) && toMinorUnits(value, currency.getDefaultFractionDigits()).isPresent();
	}

	public static boolean fitsInMinorUnits(BigDecimal value, Currency currency) {
		return hasMinorUnit(currency)
				&& minorUnitDigits(value.stripTrailingZeros(), currency.getDefaultFractionDigits()) <= MAX_DIGITS;
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
		BigDecimal stripped = value.stripTrailingZeros();
		boolean tooManyDecimals = stripped.scale() > fractionDigits;
		boolean tooLarge = minorUnitDigits(stripped, fractionDigits) > MAX_DIGITS;
		if (tooManyDecimals || tooLarge) {
			return OptionalLong.empty();
		}
		return OptionalLong.of(stripped.setScale(fractionDigits, RoundingMode.UNNECESSARY).unscaledValue().longValueExact());
	}

	private static long minorUnitDigits(BigDecimal stripped, int fractionDigits) {
		return (long) stripped.precision() - stripped.scale() + fractionDigits;
	}

}

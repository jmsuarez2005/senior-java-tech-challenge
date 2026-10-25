package com.mango.products.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;
import java.util.OptionalLong;

public record Money(long amountMinor, Currency currency) {

	// Any number with up to 18 digits fits in a long.
	private static final int MAX_DIGITS = 18;

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

	// Size is checked before scaling: rescaling a crafted value like 1e10000000 would build a ten-million-digit number.
	private static OptionalLong toMinorUnits(BigDecimal value, int fractionDigits) {
		BigDecimal stripped = value.stripTrailingZeros();
		boolean tooManyDecimals = stripped.scale() > fractionDigits;
		boolean tooLarge = stripped.precision() - stripped.scale() + fractionDigits > MAX_DIGITS;
		if (tooManyDecimals || tooLarge) {
			return OptionalLong.empty();
		}
		return OptionalLong.of(stripped.setScale(fractionDigits, RoundingMode.UNNECESSARY).unscaledValue().longValueExact());
	}

}

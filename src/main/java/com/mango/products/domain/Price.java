package com.mango.products.domain;

import java.util.Objects;

/** Immutable: a price is never edited, only added (or, at most, removed). */
public record Price(Money money, DateRange period) {

	public Price {
		Objects.requireNonNull(money, "money");
		Objects.requireNonNull(period, "period");
		if (money.amountMinor() <= 0) {
			throw new DomainValidationException("value", "value must be greater than 0");
		}
	}

	/** Prices only conflict within the same currency: each currency has its own timeline. */
	public boolean overlaps(Price other) {
		return money.currency().equals(other.money.currency()) && period.overlaps(other.period);
	}

}

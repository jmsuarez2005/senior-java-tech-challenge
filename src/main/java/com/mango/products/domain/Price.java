package com.mango.products.domain;

import java.time.LocalDate;
import java.util.Currency;
import java.util.Objects;

public record Price(Money money, DateRange period) {

	public Price {
		Objects.requireNonNull(money, "money");
		Objects.requireNonNull(period, "period");
		if (money.amountMinor() <= 0) {
			throw new DomainValidationException("value", "value must be greater than 0");
		}
	}

	public boolean overlaps(Price other) {
		return money.currency().equals(other.money.currency()) && period.overlaps(other.period);
	}

	public boolean startsOn(LocalDate initDate, Currency currency) {
		return money.currency().equals(currency) && period.initDate().equals(initDate);
	}

	public boolean appliesTo(LocalDate date, Currency currency) {
		return money.currency().equals(currency) && period.contains(date);
	}

}

package com.mango.products.adapters.out.redis;

import java.time.LocalDate;
import java.util.Currency;

import com.mango.products.domain.DateRange;
import com.mango.products.domain.Money;
import com.mango.products.domain.Price;

record CachedPrice(long amountMinor, String currency, LocalDate initDate, LocalDate endDate) {

	static CachedPrice from(Price price) {
		return new CachedPrice(price.money().amountMinor(), price.money().currency().getCurrencyCode(),
				price.period().initDate(), price.period().endDate());
	}

	Price toDomain() {
		return new Price(new Money(amountMinor, Currency.getInstance(currency)), new DateRange(initDate, endDate));
	}

}

package com.mango.products.adapters.out.redis;

import java.time.LocalDate;
import java.util.Currency;
import java.util.List;

import com.mango.products.domain.DateRange;
import com.mango.products.domain.Money;
import com.mango.products.domain.Price;
import com.mango.products.domain.Product;
import com.mango.products.domain.ProductId;

record CachedProduct(String id, String name, String description, List<CachedPrice> prices, long version) {

	record CachedPrice(long amountMinor, String currency, LocalDate initDate, LocalDate endDate) {
	}

	static CachedProduct from(Product product) {
		List<CachedPrice> prices = product.prices().stream()
				.map(price -> new CachedPrice(price.money().amountMinor(), price.money().currency().getCurrencyCode(),
						price.period().initDate(), price.period().endDate()))
				.toList();
		return new CachedProduct(product.id().value(), product.name(), product.description(), prices, product.version());
	}

	Product toDomain() {
		List<Price> domainPrices = prices.stream()
				.map(price -> new Price(new Money(price.amountMinor(), Currency.getInstance(price.currency())),
						new DateRange(price.initDate(), price.endDate())))
				.toList();
		return new Product(new ProductId(id), name, description, domainPrices, version);
	}

}

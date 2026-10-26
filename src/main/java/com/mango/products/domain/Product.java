package com.mango.products.domain;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record Product(ProductId id, String name, String description, List<Price> prices, long version) {

	private static final Comparator<Price> BY_INIT_DATE = Comparator.comparing(p -> p.period().initDate());

	public Product {
		Objects.requireNonNull(id, "id");
		if (name == null || name.isBlank()) {
			throw new DomainValidationException("name", "name is required");
		}
		prices = prices == null ? List.of() : prices.stream().sorted(BY_INIT_DATE).toList();
	}

	public static Product create(ProductId id, String name, String description) {
		return new Product(id, name, description, List.of(), 0);
	}

	public boolean hasPriceStartingOn(LocalDate initDate, Currency currency) {
		return prices.stream().anyMatch(price -> price.startsOn(initDate, currency));
	}

	public Optional<Price> priceAt(LocalDate date, Currency currency) {
		return prices.stream().filter(price -> price.appliesTo(date, currency)).findFirst();
	}

}

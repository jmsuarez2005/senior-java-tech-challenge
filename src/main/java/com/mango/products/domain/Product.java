package com.mango.products.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Aggregate root. Prices are kept ordered by {@code initDate} ascending. */
public record Product(String id, String name, String description, List<Price> prices) {

	private static final Comparator<Price> BY_INIT_DATE = Comparator.comparing(p -> p.period().initDate());

	public Product {
		Objects.requireNonNull(id, "id");
		if (name == null || name.isBlank()) {
			throw new DomainValidationException("name", "name is required");
		}
		prices = prices == null ? List.of() : prices.stream().sorted(BY_INIT_DATE).toList();
	}

}

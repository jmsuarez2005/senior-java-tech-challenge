package com.mango.products.adapters.in.web.dto;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.Currency;
import java.util.List;

import com.mango.products.domain.Price;
import com.mango.products.domain.Product;

/**
 * Filtering, sorting and paging of a product's price history. The aggregate is already loaded in one read, so this
 * runs in memory; {@code size == 0} means "everything", which keeps the statement's response unchanged by default.
 */
public record HistoryQuery(Currency currency, LocalDate from, LocalDate to, boolean descending, int page, int size) {

	public static final int MAX_PAGE_SIZE = 500;

	private static final Comparator<Price> BY_INIT_DATE = Comparator.comparing(price -> price.period().initDate());

	public boolean paginated() {
		return size > 0;
	}

	// Prices of the currency whose closed period overlaps [from, to].
	public List<Price> matching(Product product) {
		return product.prices().stream()
				.filter(price -> price.money().currency().equals(currency))
				.filter(price -> from == null || !price.period().endDate().isBefore(from))
				.filter(price -> to == null || !price.period().initDate().isAfter(to))
				.sorted(descending ? BY_INIT_DATE.reversed() : BY_INIT_DATE)
				.toList();
	}

	public List<Price> pageOf(List<Price> prices) {
		if (!paginated()) {
			return prices;
		}
		int start = Math.min(page * size, prices.size());
		return prices.subList(start, Math.min(start + size, prices.size()));
	}

	public boolean hasNextPage(int total) {
		return paginated() && (page + 1) * size < total;
	}

}

package com.mango.products.adapters.in.web.dto;

import java.util.Currency;
import java.util.List;

import com.mango.products.domain.Product;

public record PriceHistoryResponse(String name, String description, List<PriceResponse> prices) {

	public static PriceHistoryResponse from(Product product, Currency currency) {
		List<PriceResponse> prices = product.prices().stream()
				.filter(price -> price.money().currency().equals(currency))
				.map(PriceResponse::from)
				.toList();
		return new PriceHistoryResponse(product.name(), product.description(), prices);
	}

}

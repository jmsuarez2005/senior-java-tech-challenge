package com.mango.products.adapters.in.web.dto;

import java.util.List;

import com.mango.products.domain.Price;
import com.mango.products.domain.Product;

public record PriceHistoryResponse(String name, String description, List<PriceResponse> prices) {

	public static PriceHistoryResponse from(Product product, List<Price> prices) {
		return new PriceHistoryResponse(product.name(), product.description(),
				prices.stream().map(PriceResponse::from).toList());
	}

}

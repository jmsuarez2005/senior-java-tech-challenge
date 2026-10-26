package com.mango.products.adapters.in.web.dto;

import java.util.List;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import com.mango.products.domain.Price;
import com.mango.products.domain.Product;

public record PriceHistoryResponse(
		@Schema(examples = "Zapatillas deportivas") String name,
		@Schema(examples = "Modelo 2025 edición limitada") String description,
		@Schema(description = "Prices in one currency, ordered by initDate unless sort says otherwise") List<PriceResponse> prices) {

	public static PriceHistoryResponse from(Product product, List<Price> prices) {
		return new PriceHistoryResponse(product.name(), product.description(),
				prices.stream().map(PriceResponse::from).toList());
	}

}

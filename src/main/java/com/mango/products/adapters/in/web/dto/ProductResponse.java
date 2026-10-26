package com.mango.products.adapters.in.web.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import com.mango.products.domain.Product;

public record ProductResponse(
		@Schema(description = "Product id", examples = "65f0c0ffee0000000000beef") String id,
		@Schema(examples = "Zapatillas deportivas") String name,
		@Schema(examples = "Modelo 2025 edición limitada") String description) {

	public static ProductResponse from(Product product) {
		return new ProductResponse(product.id().value(), product.name(), product.description());
	}

}

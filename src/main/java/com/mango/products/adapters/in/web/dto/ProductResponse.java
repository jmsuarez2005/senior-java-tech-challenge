package com.mango.products.adapters.in.web.dto;

import com.mango.products.domain.Product;

public record ProductResponse(String id, String name, String description) {

	public static ProductResponse from(Product product) {
		return new ProductResponse(product.id().value(), product.name(), product.description());
	}

}

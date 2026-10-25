package com.mango.products.domain;

public final class ProductNotFoundException extends DomainException {

	public ProductNotFoundException(String productId) {
		super("Product " + productId + " not found");
	}

}

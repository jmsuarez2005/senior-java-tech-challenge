package com.mango.products.domain;

public final class ProductNotFoundException extends DomainException {

	public ProductNotFoundException(ProductId productId) {
		super("Product " + productId + " not found");
	}

}

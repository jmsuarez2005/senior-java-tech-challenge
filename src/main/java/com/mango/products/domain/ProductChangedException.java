package com.mango.products.domain;

public final class ProductChangedException extends DomainException {

	public ProductChangedException(ProductId productId, long expectedVersion) {
		super("Product " + productId + " has changed since version " + expectedVersion);
	}

}

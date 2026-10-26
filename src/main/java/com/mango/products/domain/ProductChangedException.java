package com.mango.products.domain;

// The client sent a version it read earlier and the product has changed since (optimistic concurrency).
public final class ProductChangedException extends DomainException {

	public ProductChangedException(ProductId productId, long expectedVersion) {
		super("Product " + productId + " has changed since version " + expectedVersion);
	}

}

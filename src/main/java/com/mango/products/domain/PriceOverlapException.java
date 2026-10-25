package com.mango.products.domain;

public final class PriceOverlapException extends DomainException {

	public PriceOverlapException(ProductId productId, Price price) {
		super("Product " + productId + " already has a " + price.money().currency()
				+ " price overlapping " + price.period());
	}

}

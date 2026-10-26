package com.mango.products.domain;

public abstract sealed class DomainException extends RuntimeException
		permits DomainValidationException, ProductNotFoundException, PriceNotFoundException, PriceOverlapException,
		ProductChangedException {

	protected DomainException(String message) {
		super(message, null, false, false);
	}

}

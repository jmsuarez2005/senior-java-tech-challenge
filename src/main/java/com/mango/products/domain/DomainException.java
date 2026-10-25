package com.mango.products.domain;

// No stack trace: these are expected outcomes (a 409 under load), not bugs.
public abstract sealed class DomainException extends RuntimeException
		permits DomainValidationException, ProductNotFoundException, PriceNotFoundException, PriceOverlapException {

	protected DomainException(String message) {
		super(message, null, false, false);
	}

}

package com.mango.products.domain;

/**
 * Base of all business-rule violations. Stack traces are skipped: these are expected outcomes
 * (e.g. a 409 under load), not bugs, and filling the trace is the expensive part of an exception.
 */
public abstract sealed class DomainException extends RuntimeException
		permits DomainValidationException, ProductNotFoundException, PriceNotFoundException, PriceOverlapException {

	protected DomainException(String message) {
		super(message, null, false, false);
	}

}

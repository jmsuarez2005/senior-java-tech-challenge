package com.mango.products.domain;

public final class DomainValidationException extends DomainException {

	private final String field;

	public DomainValidationException(String field, String message) {
		super(message);
		this.field = field;
	}

	public String field() {
		return field;
	}

}

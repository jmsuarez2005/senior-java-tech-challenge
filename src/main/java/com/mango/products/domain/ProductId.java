package com.mango.products.domain;

public record ProductId(String value) {

	public ProductId {
		if (value == null || value.isBlank()) {
			throw new DomainValidationException("id", "product id is required");
		}
	}

	@Override
	public String toString() {
		return value;
	}

}

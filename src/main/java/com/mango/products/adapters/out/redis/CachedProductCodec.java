package com.mango.products.adapters.out.redis;

import java.io.UncheckedIOException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mango.products.domain.Product;

public class CachedProductCodec {

	private final ObjectMapper json;

	public CachedProductCodec(ObjectMapper json) {
		this.json = json;
	}

	String encode(Product product) {
		try {
			return json.writeValueAsString(CachedProduct.from(product));
		}
		catch (JsonProcessingException e) {
			throw new UncheckedIOException(e);
		}
	}

	Product decode(String value) {
		try {
			return json.readValue(value, CachedProduct.class).toDomain();
		}
		catch (JsonProcessingException e) {
			throw new UncheckedIOException(e);
		}
	}

}

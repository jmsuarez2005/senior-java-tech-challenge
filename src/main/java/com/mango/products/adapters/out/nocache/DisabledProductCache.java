package com.mango.products.adapters.out.nocache;

import com.mango.products.application.port.out.ProductCache;
import com.mango.products.domain.Product;
import com.mango.products.domain.ProductId;

import io.smallrye.mutiny.Uni;

public class DisabledProductCache implements ProductCache {

	@Override
	public Uni<Product> get(ProductId id) {
		return Uni.createFrom().nullItem();
	}

	@Override
	public Uni<Void> put(Product product) {
		return Uni.createFrom().voidItem();
	}

}

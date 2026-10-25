package com.mango.products.application;

import com.mango.products.application.port.out.ProductCache;
import com.mango.products.application.port.out.ProductRepository;
import com.mango.products.domain.Product;
import com.mango.products.domain.ProductId;
import com.mango.products.domain.ProductNotFoundException;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class FindProduct {

	private final ProductRepository products;
	private final ProductCache cache;

	public FindProduct(ProductRepository products, ProductCache cache) {
		this.products = products;
		this.cache = cache;
	}

	public Uni<Product> execute(ProductId productId) {
		return cache.get(productId)
				.onItem().ifNull().switchTo(() -> products.findById(productId)
						.onItem().ifNotNull().call(cache::put))
				.onItem().ifNull().failWith(() -> new ProductNotFoundException(productId));
	}

}

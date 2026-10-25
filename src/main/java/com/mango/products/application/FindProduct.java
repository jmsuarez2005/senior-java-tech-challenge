package com.mango.products.application;

import com.mango.products.application.port.out.ProductCache;
import com.mango.products.application.port.out.ProductRepository;
import com.mango.products.domain.Product;
import com.mango.products.domain.ProductId;
import com.mango.products.domain.ProductNotFoundException;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class FindProduct {

	private final ProductRepository products;
	private final ProductCache cache;

	public FindProduct(ProductRepository products, ProductCache cache) {
		this.products = products;
		this.cache = cache;
	}

	public Product execute(ProductId productId) {
		return cache.get(productId).orElseGet(() -> {
			Product product = products.findById(productId).orElseThrow(() -> new ProductNotFoundException(productId));
			cache.put(product);
			return product;
		});
	}

}

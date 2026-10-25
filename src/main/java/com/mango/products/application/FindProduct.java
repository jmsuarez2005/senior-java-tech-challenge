package com.mango.products.application;

import org.springframework.stereotype.Service;

import com.mango.products.application.port.out.ProductCache;
import com.mango.products.application.port.out.ProductRepository;
import com.mango.products.domain.Product;
import com.mango.products.domain.ProductId;
import com.mango.products.domain.ProductNotFoundException;

import reactor.core.publisher.Mono;

@Service
public class FindProduct {

	private final ProductRepository products;
	private final ProductCache cache;

	public FindProduct(ProductRepository products, ProductCache cache) {
		this.products = products;
		this.cache = cache;
	}

	public Mono<Product> execute(ProductId productId) {
		return cache.get(productId)
				.switchIfEmpty(Mono.defer(() -> products.findById(productId)
						.flatMap(product -> cache.put(product).thenReturn(product))))
				.switchIfEmpty(Mono.error(() -> new ProductNotFoundException(productId)));
	}

}

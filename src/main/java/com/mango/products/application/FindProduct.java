package com.mango.products.application;

import org.springframework.stereotype.Service;

import com.mango.products.application.port.out.ProductRepository;
import com.mango.products.domain.Product;
import com.mango.products.domain.ProductId;
import com.mango.products.domain.ProductNotFoundException;

import reactor.core.publisher.Mono;

@Service
public class FindProduct {

	private final ProductRepository products;

	public FindProduct(ProductRepository products) {
		this.products = products;
	}

	public Mono<Product> execute(ProductId productId) {
		return products.findById(productId)
				.switchIfEmpty(Mono.error(() -> new ProductNotFoundException(productId)));
	}

}

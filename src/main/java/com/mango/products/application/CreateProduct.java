package com.mango.products.application;

import org.springframework.stereotype.Service;

import com.mango.products.application.port.out.ProductRepository;
import com.mango.products.domain.Product;

import reactor.core.publisher.Mono;

@Service
public class CreateProduct {

	private final ProductRepository products;

	public CreateProduct(ProductRepository products) {
		this.products = products;
	}

	public Mono<Product> execute(String name, String description) {
		return Mono.fromCallable(() -> Product.create(products.nextId(), name, description))
				.flatMap(products::create);
	}

}

package com.mango.products.application;

import java.util.List;

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
		return Mono.fromCallable(() -> new Product(products.nextId(), name, description, List.of()))
				.flatMap(products::create);
	}

}

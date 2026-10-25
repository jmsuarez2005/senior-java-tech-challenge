package com.mango.products.application.port.out;

import com.mango.products.domain.Price;
import com.mango.products.domain.Product;
import com.mango.products.domain.ProductId;

import reactor.core.publisher.Mono;

public interface ProductRepository {

	ProductId nextId();

	Mono<Product> create(Product product);

	Mono<Product> findById(ProductId id);

	/**
	 * Adds the price in a single atomic write, only if no price of the same currency overlaps it,
	 * and returns the product as stored after the write.
	 * Fails with {@code PriceOverlapException} or {@code ProductNotFoundException}.
	 */
	Mono<Product> addPriceIfNoOverlap(ProductId id, Price price);

}

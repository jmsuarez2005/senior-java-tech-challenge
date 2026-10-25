package com.mango.products.application.port.out;

import com.mango.products.domain.Product;
import com.mango.products.domain.ProductId;

import reactor.core.publisher.Mono;

/**
 * Best-effort cache: a miss or an unavailable cache is an empty result, never an error,
 * and {@link #put} never replaces a product with an older version of itself.
 */
public interface ProductCache {

	Mono<Product> get(ProductId id);

	Mono<Void> put(Product product);

}

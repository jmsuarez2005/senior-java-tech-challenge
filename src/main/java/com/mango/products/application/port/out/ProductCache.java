package com.mango.products.application.port.out;

import java.util.Optional;

import com.mango.products.domain.Product;
import com.mango.products.domain.ProductId;

/**
 * Best-effort cache: a miss or an unavailable cache is an empty result, never an exception,
 * and {@link #put} never replaces a product with an older version of itself.
 */
public interface ProductCache {

	Optional<Product> get(ProductId id);

	void put(Product product);

}

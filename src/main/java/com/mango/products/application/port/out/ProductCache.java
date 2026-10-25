package com.mango.products.application.port.out;

import com.mango.products.domain.Product;
import com.mango.products.domain.ProductId;

import io.smallrye.mutiny.Uni;

/**
 * Best-effort cache: a miss or an unavailable cache emits {@code null}, never a failure,
 * and {@link #put} never replaces a product with an older version of itself.
 */
public interface ProductCache {

	Uni<Product> get(ProductId id);

	Uni<Void> put(Product product);

}

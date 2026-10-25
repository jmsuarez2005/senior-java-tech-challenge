package com.mango.products.application.port.out;

import com.mango.products.domain.Price;
import com.mango.products.domain.Product;
import com.mango.products.domain.ProductId;

import io.smallrye.mutiny.Uni;

public interface ProductRepository {

	ProductId nextId();

	Uni<Product> create(Product product);

	/** Emits {@code null} when the product does not exist. */
	Uni<Product> findById(ProductId id);

	/**
	 * Adds the price in a single atomic write, only if no price of the same currency overlaps it,
	 * and emits the product as stored after the write.
	 * Fails with {@code PriceOverlapException} or {@code ProductNotFoundException}.
	 */
	Uni<Product> addPriceIfNoOverlap(ProductId id, Price price);

}

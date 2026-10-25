package com.mango.products.application.port.out;

import java.util.Optional;

import com.mango.products.domain.Price;
import com.mango.products.domain.Product;
import com.mango.products.domain.ProductId;

public interface ProductRepository {

	ProductId nextId();

	Product create(Product product);

	Optional<Product> findById(ProductId id);

	/**
	 * Adds the price in a single atomic write, only if no price of the same currency overlaps it,
	 * and returns the product as stored after the write.
	 *
	 * @throws com.mango.products.domain.PriceOverlapException if a price of the same currency overlaps it
	 * @throws com.mango.products.domain.ProductNotFoundException if the product does not exist
	 */
	Product addPriceIfNoOverlap(ProductId id, Price price);

}

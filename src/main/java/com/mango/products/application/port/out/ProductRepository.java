package com.mango.products.application.port.out;

import java.time.LocalDate;
import java.util.Currency;
import java.util.OptionalLong;

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

	/**
	 * Replaces the price identified by its currency and {@code initDate} with {@code price}, in one atomic write, only
	 * if no other price of that currency overlaps the new period and, when {@code expectedVersion} is given, only if
	 * the product is still at that version. Emits the product as stored after the write.
	 * Fails with {@code PriceNotFoundException}, {@code PriceOverlapException}, {@code ProductChangedException} or
	 * {@code ProductNotFoundException}.
	 */
	Uni<Product> replacePriceIfNoOverlap(ProductId id, Price price, OptionalLong expectedVersion);

	/**
	 * Removes the price identified by its currency and {@code initDate}, when {@code expectedVersion} is given only if
	 * the product is still at that version, and emits the product as stored after the write.
	 * Fails with {@code PriceNotFoundException}, {@code ProductChangedException} or {@code ProductNotFoundException}.
	 */
	Uni<Product> removePrice(ProductId id, Currency currency, LocalDate initDate, OptionalLong expectedVersion);

}

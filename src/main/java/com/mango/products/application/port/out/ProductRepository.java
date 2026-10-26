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

	Uni<Product> findById(ProductId id);

	Uni<Product> addPriceIfNoOverlap(ProductId id, Price price);

	Uni<Product> replacePriceIfNoOverlap(ProductId id, Price price, OptionalLong expectedVersion);

	Uni<Product> removePrice(ProductId id, Currency currency, LocalDate initDate, OptionalLong expectedVersion);

}

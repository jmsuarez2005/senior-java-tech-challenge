package com.mango.products.application;

import java.time.LocalDate;
import java.util.Currency;

import com.mango.products.domain.Price;
import com.mango.products.domain.PriceNotFoundException;
import com.mango.products.domain.ProductId;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class FindPriceInForce {

	private final FindProduct findProduct;

	public FindPriceInForce(FindProduct findProduct) {
		this.findProduct = findProduct;
	}

	public Uni<Price> execute(ProductId productId, LocalDate date, Currency currency) {
		return findProduct.execute(productId)
				.map(product -> product.priceAt(date, currency).orElse(null))
				.onItem().ifNull().failWith(() -> new PriceNotFoundException(productId, date, currency));
	}

}

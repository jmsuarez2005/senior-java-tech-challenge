package com.mango.products.application;

import java.time.LocalDate;
import java.util.Currency;

import org.springframework.stereotype.Service;

import com.mango.products.domain.Price;
import com.mango.products.domain.PriceNotFoundException;
import com.mango.products.domain.ProductId;

import reactor.core.publisher.Mono;

@Service
public class FindPriceInForce {

	private final FindProduct findProduct;

	public FindPriceInForce(FindProduct findProduct) {
		this.findProduct = findProduct;
	}

	public Mono<Price> execute(ProductId productId, LocalDate date, Currency currency) {
		return findProduct.execute(productId)
				.flatMap(product -> Mono.justOrEmpty(product.priceAt(date, currency)))
				.switchIfEmpty(Mono.error(() -> new PriceNotFoundException(productId, date, currency)));
	}

}

package com.mango.products.application;

import java.time.LocalDate;
import java.util.Currency;

import org.springframework.stereotype.Service;

import com.mango.products.application.port.out.ProductRepository;
import com.mango.products.domain.Price;
import com.mango.products.domain.PriceNotFoundException;
import com.mango.products.domain.ProductId;

import reactor.core.publisher.Mono;

@Service
public class FindPriceInForce {

	private final ProductRepository products;

	public FindPriceInForce(ProductRepository products) {
		this.products = products;
	}

	public Mono<Price> execute(ProductId productId, LocalDate date, Currency currency) {
		return products.findPriceAt(productId, date, currency)
				.switchIfEmpty(Mono.error(() -> new PriceNotFoundException(productId, date, currency)));
	}

}

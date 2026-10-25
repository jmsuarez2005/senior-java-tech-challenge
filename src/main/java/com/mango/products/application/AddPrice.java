package com.mango.products.application;

import org.springframework.stereotype.Service;

import com.mango.products.application.port.out.ProductRepository;
import com.mango.products.domain.Price;
import com.mango.products.domain.ProductId;

import reactor.core.publisher.Mono;

@Service
public class AddPrice {

	private final ProductRepository products;

	public AddPrice(ProductRepository products) {
		this.products = products;
	}

	public Mono<Price> execute(ProductId productId, Price price) {
		return products.addPriceIfNoOverlap(productId, price).thenReturn(price);
	}

}

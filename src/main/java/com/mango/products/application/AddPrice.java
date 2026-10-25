package com.mango.products.application;

import org.springframework.stereotype.Service;

import com.mango.products.application.port.out.ProductCache;
import com.mango.products.application.port.out.ProductRepository;
import com.mango.products.domain.Price;
import com.mango.products.domain.ProductId;

import reactor.core.publisher.Mono;

@Service
public class AddPrice {

	private final ProductRepository products;
	private final ProductCache cache;

	public AddPrice(ProductRepository products, ProductCache cache) {
		this.products = products;
		this.cache = cache;
	}

	public Mono<Price> execute(ProductId productId, Price price) {
		return products.addPriceIfNoOverlap(productId, price)
				.flatMap(cache::put)
				.thenReturn(price);
	}

}

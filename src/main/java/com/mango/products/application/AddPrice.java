package com.mango.products.application;

import com.mango.products.application.port.out.ProductCache;
import com.mango.products.application.port.out.ProductRepository;
import com.mango.products.domain.Price;
import com.mango.products.domain.ProductId;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AddPrice {

	private final ProductRepository products;
	private final ProductCache cache;

	public AddPrice(ProductRepository products, ProductCache cache) {
		this.products = products;
		this.cache = cache;
	}

	public Uni<Price> execute(ProductId productId, Price price) {
		return products.addPriceIfNoOverlap(productId, price)
				.call(cache::put)
				.replaceWith(price);
	}

}

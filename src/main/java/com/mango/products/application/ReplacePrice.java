package com.mango.products.application;

import org.jboss.logging.Logger;

import com.mango.products.application.port.out.ProductCache;
import com.mango.products.application.port.out.ProductRepository;
import com.mango.products.domain.Price;
import com.mango.products.domain.ProductId;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

// "Updating" a price: same currency and initDate, new value and/or end date.
@ApplicationScoped
public class ReplacePrice {

	private static final Logger log = Logger.getLogger(ReplacePrice.class);

	private final ProductRepository products;
	private final ProductCache cache;

	public ReplacePrice(ProductRepository products, ProductCache cache) {
		this.products = products;
		this.cache = cache;
	}

	public Uni<Price> execute(ProductId productId, Price price) {
		return products.replacePriceIfNoOverlap(productId, price)
				.invoke(product -> log.infof("Price replaced: product=%s currency=%s period=%s version=%d",
						productId, price.money().currency(), price.period(), product.version()))
				.call(cache::put)
				.replaceWith(price);
	}

}

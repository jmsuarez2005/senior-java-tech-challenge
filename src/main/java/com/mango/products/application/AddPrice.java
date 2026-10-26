package com.mango.products.application;

import org.jboss.logging.Logger;

import com.mango.products.application.port.out.ProductCache;
import com.mango.products.application.port.out.ProductRepository;
import com.mango.products.domain.Price;
import com.mango.products.domain.PriceOverlapException;
import com.mango.products.domain.ProductId;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AddPrice {

	private static final Logger log = Logger.getLogger(AddPrice.class);

	private final ProductRepository products;
	private final ProductCache cache;

	public AddPrice(ProductRepository products, ProductCache cache) {
		this.products = products;
		this.cache = cache;
	}

	public Uni<Price> execute(ProductId productId, Price price) {
		return products.addPriceIfNoOverlap(productId, price)
				.invoke(product -> log.infof("Price added: product=%s currency=%s period=%s version=%d",
						productId, price.money().currency(), price.period(), product.version()))
				.onFailure(PriceOverlapException.class).invoke(e -> log.infof("Price rejected: %s", e.getMessage()))
				.call(cache::put)
				.replaceWith(price);
	}

}

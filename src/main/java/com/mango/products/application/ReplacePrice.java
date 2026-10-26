package com.mango.products.application;

import java.util.OptionalLong;

import org.jboss.logging.Logger;

import com.mango.products.application.port.out.ProductCache;
import com.mango.products.application.port.out.PriceWriter;
import com.mango.products.domain.Price;
import com.mango.products.domain.Product;
import com.mango.products.domain.ProductId;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ReplacePrice {

	private static final Logger log = Logger.getLogger(ReplacePrice.class);

	private final PriceWriter prices;
	private final ProductCache cache;

	public ReplacePrice(PriceWriter prices, ProductCache cache) {
		this.prices = prices;
		this.cache = cache;
	}

	public Uni<Product> execute(ProductId productId, Price price, OptionalLong expectedVersion) {
		return prices.replacePriceIfNoOverlap(productId, price, expectedVersion)
				.invoke(product -> log.infof("Price replaced: product=%s currency=%s period=%s version=%d",
						productId, price.money().currency(), price.period(), product.version()))
				.call(cache::put);
	}

}

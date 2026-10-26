package com.mango.products.application;

import java.time.LocalDate;
import java.util.Currency;

import org.jboss.logging.Logger;

import com.mango.products.application.port.out.ProductCache;
import com.mango.products.application.port.out.ProductRepository;
import com.mango.products.domain.ProductId;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class RemovePrice {

	private static final Logger log = Logger.getLogger(RemovePrice.class);

	private final ProductRepository products;
	private final ProductCache cache;

	public RemovePrice(ProductRepository products, ProductCache cache) {
		this.products = products;
		this.cache = cache;
	}

	public Uni<Void> execute(ProductId productId, Currency currency, LocalDate initDate) {
		return products.removePrice(productId, currency, initDate)
				.invoke(product -> log.infof("Price removed: product=%s currency=%s initDate=%s version=%d",
						productId, currency, initDate, product.version()))
				.call(cache::put)
				.replaceWithVoid();
	}

}

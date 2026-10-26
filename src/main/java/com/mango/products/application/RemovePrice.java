package com.mango.products.application;

import java.time.LocalDate;
import java.util.Currency;
import java.util.OptionalLong;

import org.jboss.logging.Logger;

import com.mango.products.application.port.out.ProductCache;
import com.mango.products.application.port.out.ProductRepository;
import com.mango.products.domain.Product;
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

	public Uni<Product> execute(ProductId productId, Currency currency, LocalDate initDate,
			OptionalLong expectedVersion) {
		return products.removePrice(productId, currency, initDate, expectedVersion)
				.invoke(product -> log.infof("Price removed: product=%s currency=%s initDate=%s version=%d",
						productId, currency, initDate, product.version()))
				.call(cache::put);
	}

}

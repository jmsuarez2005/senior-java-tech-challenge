package com.mango.products.application;

import org.jboss.logging.Logger;

import com.mango.products.application.port.out.ProductWriter;
import com.mango.products.domain.Product;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CreateProduct {

	private static final Logger log = Logger.getLogger(CreateProduct.class);

	private final ProductWriter products;

	public CreateProduct(ProductWriter products) {
		this.products = products;
	}

	public Uni<Product> execute(String name, String description) {
		return Uni.createFrom().item(() -> Product.create(products.nextId(), name, description))
				.chain(products::create)
				.invoke(product -> log.infof("Product created: id=%s", product.id()));
	}

}

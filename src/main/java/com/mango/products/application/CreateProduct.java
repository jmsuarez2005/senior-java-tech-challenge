package com.mango.products.application;

import com.mango.products.application.port.out.ProductRepository;
import com.mango.products.domain.Product;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CreateProduct {

	private final ProductRepository products;

	public CreateProduct(ProductRepository products) {
		this.products = products;
	}

	public Uni<Product> execute(String name, String description) {
		return Uni.createFrom().item(() -> Product.create(products.nextId(), name, description))
				.chain(products::create);
	}

}

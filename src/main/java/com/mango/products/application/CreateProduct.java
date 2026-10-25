package com.mango.products.application;

import com.mango.products.application.port.out.ProductRepository;
import com.mango.products.domain.Product;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CreateProduct {

	private final ProductRepository products;

	public CreateProduct(ProductRepository products) {
		this.products = products;
	}

	public Product execute(String name, String description) {
		return products.create(Product.create(products.nextId(), name, description));
	}

}

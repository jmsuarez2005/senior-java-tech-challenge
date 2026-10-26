package com.mango.products.application.port.out;

import com.mango.products.domain.Product;
import com.mango.products.domain.ProductId;

import io.smallrye.mutiny.Uni;

public interface ProductWriter {

	ProductId nextId();

	Uni<Product> create(Product product);

}

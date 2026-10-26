package com.mango.products.application.port.out;

import com.mango.products.domain.Product;
import com.mango.products.domain.ProductId;

import io.smallrye.mutiny.Uni;

public interface ProductReader {

	Uni<Product> findById(ProductId id);

}

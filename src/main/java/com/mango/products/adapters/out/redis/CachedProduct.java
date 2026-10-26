package com.mango.products.adapters.out.redis;

import java.util.List;

import com.mango.products.domain.Product;
import com.mango.products.domain.ProductId;

record CachedProduct(String id, String name, String description, List<CachedPrice> prices, long version) {

	static CachedProduct from(Product product) {
		return new CachedProduct(product.id().value(), product.name(), product.description(),
				product.prices().stream().map(CachedPrice::from).toList(), product.version());
	}

	Product toDomain() {
		return new Product(new ProductId(id), name, description, prices.stream().map(CachedPrice::toDomain).toList(),
				version);
	}

}

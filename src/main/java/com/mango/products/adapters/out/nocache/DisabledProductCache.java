package com.mango.products.adapters.out.nocache;

import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;

import com.mango.products.application.port.out.ProductCache;
import com.mango.products.domain.Product;
import com.mango.products.domain.ProductId;

@Component
@ConditionalOnBooleanProperty(name = "products.cache.enabled", havingValue = false, matchIfMissing = true)
class DisabledProductCache implements ProductCache {

	@Override
	public Optional<Product> get(ProductId id) {
		return Optional.empty();
	}

	@Override
	public void put(Product product) {
	}

}

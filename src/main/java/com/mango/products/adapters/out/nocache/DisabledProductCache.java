package com.mango.products.adapters.out.nocache;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;

import com.mango.products.application.port.out.ProductCache;
import com.mango.products.domain.Product;
import com.mango.products.domain.ProductId;

import reactor.core.publisher.Mono;

@Component
@ConditionalOnBooleanProperty(name = "products.cache.enabled", havingValue = false, matchIfMissing = true)
class DisabledProductCache implements ProductCache {

	@Override
	public Mono<Product> get(ProductId id) {
		return Mono.empty();
	}

	@Override
	public Mono<Void> put(Product product) {
		return Mono.empty();
	}

}

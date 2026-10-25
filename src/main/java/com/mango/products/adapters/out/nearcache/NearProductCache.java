package com.mango.products.adapters.out.nearcache;

import java.time.Duration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mango.products.application.port.out.ProductCache;
import com.mango.products.domain.Product;
import com.mango.products.domain.ProductId;

import io.smallrye.mutiny.Uni;

/**
 * In-process first level in front of a shared cache. A hit costs a map lookup: no network, no JSON, no rebuilding of
 * the aggregate. Local writes refresh it at once; changes made by other instances show up after {@code ttl} at most.
 */
public class NearProductCache implements ProductCache {

	private final Cache<ProductId, Product> local;
	private final ProductCache shared;

	public NearProductCache(ProductCache shared, Duration ttl, long maxEntries) {
		this.shared = shared;
		this.local = Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(maxEntries).build();
	}

	@Override
	public Uni<Product> get(ProductId id) {
		Product hit = local.getIfPresent(id);
		if (hit != null) {
			return Uni.createFrom().item(hit);
		}
		return shared.get(id).onItem().ifNotNull().invoke(this::keepNewest);
	}

	@Override
	public Uni<Void> put(Product product) {
		keepNewest(product);
		return shared.put(product);
	}

	// Same rule as the shared cache: an older copy of the aggregate never replaces a newer one.
	private void keepNewest(Product product) {
		local.asMap().merge(product.id(), product,
				(cached, candidate) -> candidate.version() >= cached.version() ? candidate : cached);
	}

}

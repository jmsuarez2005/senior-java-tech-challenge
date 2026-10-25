package com.mango.products.adapters.out.nearcache;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.mango.products.application.port.out.ProductCache;
import com.mango.products.domain.Product;
import com.mango.products.domain.ProductId;

import io.smallrye.mutiny.Uni;

class NearProductCacheTest {

	private static final ProductId ID = new ProductId("p1");

	private final CountingCache shared = new CountingCache();
	private final NearProductCache cache = new NearProductCache(shared, Duration.ofMinutes(1), 100);

	@Test
	void missGoesToTheSharedCacheOnceThenHitsLocally() {
		shared.store(product(1));

		assertThat(get()).isEqualTo(product(1));
		assertThat(get()).isEqualTo(product(1));
		assertThat(shared.reads).isEqualTo(1);
	}

	@Test
	void missEverywhereIsNull() {
		assertThat(get()).isNull();
	}

	@Test
	void putRefreshesTheLocalCopyAndTheSharedOne() {
		shared.store(product(1));
		get();

		cache.put(product(2)).await().indefinitely();

		assertThat(get()).isEqualTo(product(2));
		assertThat(shared.stored.get(ID)).isEqualTo(product(2));
	}

	@Test
	void anOlderCopyFromTheSharedCacheNeverReplacesANewerLocalOne() {
		cache.put(product(3)).await().indefinitely();
		shared.store(product(2));

		assertThat(get()).isEqualTo(product(3));
	}

	private Product get() {
		return cache.get(ID).await().indefinitely();
	}

	private static Product product(long version) {
		return new Product(ID, "Sneakers", null, List.of(), version);
	}

	private static final class CountingCache implements ProductCache {

		private final Map<ProductId, Product> stored = new HashMap<>();
		private int reads;

		void store(Product product) {
			stored.put(product.id(), product);
		}

		@Override
		public Uni<Product> get(ProductId id) {
			reads++;
			return Uni.createFrom().item(stored.get(id));
		}

		@Override
		public Uni<Void> put(Product product) {
			store(product);
			return Uni.createFrom().voidItem();
		}

	}

}

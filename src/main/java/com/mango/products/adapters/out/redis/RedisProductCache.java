package com.mango.products.adapters.out.redis;

import java.io.UncheckedIOException;
import java.time.Duration;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mango.products.application.port.out.ProductCache;
import com.mango.products.domain.Product;
import com.mango.products.domain.ProductId;

import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.value.ReactiveValueCommands;
import io.smallrye.mutiny.Uni;

public class RedisProductCache implements ProductCache {

	private static final Logger log = Logger.getLogger(RedisProductCache.class);

	// Compare-and-set on the aggregate version, so a slow writer can never bring back an older copy.
	private static final String PUT_IF_NEWER = """
			local current = redis.call('GET', KEYS[1])
			if current and cjson.decode(current)['version'] >= tonumber(ARGV[2]) then
			  return 0
			end
			redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[3])
			return 1
			""";

	private final ReactiveRedisDataSource redis;
	private final ReactiveValueCommands<String, String> values;
	private final ObjectMapper json;
	private final String ttlSeconds;

	public RedisProductCache(ReactiveRedisDataSource redis, ObjectMapper json, Duration ttl) {
		this.redis = redis;
		this.values = redis.value(String.class);
		this.json = json;
		this.ttlSeconds = Long.toString(ttl.toSeconds());
	}

	@Override
	public Uni<Product> get(ProductId id) {
		return values.get(key(id))
				.onItem().ifNotNull().transform(value -> read(value).toDomain())
				.onFailure().recoverWithItem(e -> {
					log.warnf("Cache read failed for product %s: %s", id, e);
					return null;
				});
	}

	@Override
	public Uni<Void> put(Product product) {
		return Uni.createFrom().item(() -> write(CachedProduct.from(product)))
				.chain(value -> redis.execute("EVAL", PUT_IF_NEWER, "1", key(product.id()), value,
						Long.toString(product.version()), ttlSeconds))
				.replaceWithVoid()
				.onFailure().recoverWithItem(e -> {
					log.warnf("Cache write failed for product %s: %s", product.id(), e);
					return null;
				});
	}

	private CachedProduct read(String value) {
		try {
			return json.readValue(value, CachedProduct.class);
		}
		catch (JsonProcessingException e) {
			throw new UncheckedIOException(e);
		}
	}

	private String write(CachedProduct product) {
		try {
			return json.writeValueAsString(product);
		}
		catch (JsonProcessingException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static String key(ProductId id) {
		return "product:" + id.value();
	}

}

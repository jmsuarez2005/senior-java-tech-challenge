package com.mango.products.adapters.out.redis;

import java.time.Duration;
import java.util.Optional;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mango.products.application.port.out.ProductCache;
import com.mango.products.domain.Product;
import com.mango.products.domain.ProductId;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;

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

	private final RedisDataSource redis;
	private final ValueCommands<String, String> values;
	private final ObjectMapper json;
	private final String ttlSeconds;

	public RedisProductCache(RedisDataSource redis, ObjectMapper json, Duration ttl) {
		this.redis = redis;
		this.values = redis.value(String.class);
		this.json = json;
		this.ttlSeconds = Long.toString(ttl.toSeconds());
	}

	@Override
	public Optional<Product> get(ProductId id) {
		try {
			String value = values.get(key(id));
			return value == null ? Optional.empty() : Optional.of(json.readValue(value, CachedProduct.class).toDomain());
		}
		catch (JsonProcessingException | RuntimeException e) {
			log.warnf("Cache read failed for product %s: %s", id, e);
			return Optional.empty();
		}
	}

	@Override
	public void put(Product product) {
		try {
			String value = json.writeValueAsString(CachedProduct.from(product));
			redis.execute("EVAL", PUT_IF_NEWER, "1", key(product.id()), value, Long.toString(product.version()), ttlSeconds);
		}
		catch (JsonProcessingException | RuntimeException e) {
			log.warnf("Cache write failed for product %s: %s", product.id(), e);
		}
	}

	private static String key(ProductId id) {
		return "product:" + id.value();
	}

}

package com.mango.products.adapters.out.redis;

import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import com.mango.products.application.port.out.ProductCache;
import com.mango.products.domain.Product;
import com.mango.products.domain.ProductId;

import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

@Component
@ConditionalOnBooleanProperty("products.cache.enabled")
class RedisProductCache implements ProductCache {

	private static final Logger log = LoggerFactory.getLogger(RedisProductCache.class);

	// Compare-and-set on the aggregate version, so a slow writer can never bring back an older copy.
	private static final RedisScript<Long> PUT_IF_NEWER = RedisScript.of("""
			local current = redis.call('GET', KEYS[1])
			if current and cjson.decode(current)['version'] >= tonumber(ARGV[2]) then
			  return 0
			end
			redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[3])
			return 1
			""", Long.class);

	private final ReactiveStringRedisTemplate redis;
	private final JsonMapper json;
	private final String ttlSeconds;

	RedisProductCache(ReactiveStringRedisTemplate redis, JsonMapper json,
			@Value("${products.cache.ttl:10m}") Duration ttl) {
		this.redis = redis;
		this.json = json;
		this.ttlSeconds = Long.toString(ttl.toSeconds());
	}

	@Override
	public Mono<Product> get(ProductId id) {
		return redis.opsForValue().get(key(id))
				.flatMap(value -> Mono.fromCallable(() -> json.readValue(value, CachedProduct.class).toDomain()))
				.onErrorResume(e -> {
					log.warn("Cache read failed for product {}: {}", id, e.toString());
					return Mono.empty();
				});
	}

	@Override
	public Mono<Void> put(Product product) {
		return Mono.fromCallable(() -> json.writeValueAsString(CachedProduct.from(product)))
				.flatMap(value -> redis.execute(PUT_IF_NEWER, List.of(key(product.id())),
						List.of(value, Long.toString(product.version()), ttlSeconds)).then())
				.onErrorResume(e -> {
					log.warn("Cache write failed for product {}: {}", product.id(), e.toString());
					return Mono.empty();
				});
	}

	private static String key(ProductId id) {
		return "product:" + id.value();
	}

}

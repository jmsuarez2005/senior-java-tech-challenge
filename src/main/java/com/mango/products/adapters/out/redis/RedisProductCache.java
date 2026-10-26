package com.mango.products.adapters.out.redis;

import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mango.products.application.port.out.ProductCache;
import com.mango.products.domain.Product;
import com.mango.products.domain.ProductId;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.value.ReactiveValueCommands;
import io.smallrye.faulttolerance.api.Guard;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.util.TypeLiteral;

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

	private static final TypeLiteral<Uni<String>> UNI_STRING = new TypeLiteral<>() {
	};
	private static final TypeLiteral<Uni<Void>> UNI_VOID = new TypeLiteral<>() {
	};

	private final ReactiveRedisDataSource redis;
	private final ReactiveValueCommands<String, String> values;
	private final ObjectMapper json;
	private final String ttlSeconds;
	private final Counter hits;
	private final Counter misses;
	private final Counter readErrors;
	private final Counter writeErrors;

	// One breaker for every Redis call: when Redis is slow or down, requests skip it instead of each paying the timeout.
	private final Guard redisCircuit = Guard.create()
			.withDescription("redis-product-cache")
			.withCircuitBreaker()
				.requestVolumeThreshold(20)
				.failureRatio(0.5)
				.delay(5, ChronoUnit.SECONDS)
				.onStateChange(state -> log.warnf("Redis cache circuit is now %s", state))
				.done()
			.build();

	public RedisProductCache(ReactiveRedisDataSource redis, ObjectMapper json, Duration ttl, MeterRegistry meters) {
		this.redis = redis;
		this.values = redis.value(String.class);
		this.json = json;
		this.ttlSeconds = Long.toString(ttl.toSeconds());
		this.hits = meters.counter("products.cache.reads", "result", "hit");
		this.misses = meters.counter("products.cache.reads", "result", "miss");
		this.readErrors = meters.counter("products.cache.reads", "result", "error");
		this.writeErrors = meters.counter("products.cache.writes", "result", "error");
	}

	@Override
	public Uni<Product> get(ProductId id) {
		return redisCircuit.get(() -> values.get(key(id)), UNI_STRING)
				.onItem().invoke(value -> (value == null ? misses : hits).increment())
				.onItem().ifNotNull().transform(value -> read(value).toDomain())
				.onFailure().recoverWithItem(e -> {
					readErrors.increment();
					logFailure("read", id, e);
					return null;
				});
	}

	// If the new version cannot be stored, the old one is deleted: a stale price must never outlive a write.
	@Override
	public Uni<Void> put(Product product) {
		return redisCircuit.get(() -> Uni.createFrom().item(() -> write(CachedProduct.from(product)))
						.chain(value -> redis.execute("EVAL", PUT_IF_NEWER, "1", key(product.id()), value,
								Long.toString(product.version()), ttlSeconds))
						.replaceWithVoid(), UNI_VOID)
				.onFailure().call(e -> evict(product.id()))
				.onFailure().recoverWithItem(e -> {
					writeErrors.increment();
					logFailure("write", product.id(), e);
					return null;
				});
	}

	private Uni<Void> evict(ProductId id) {
		return redisCircuit.get(() -> redis.key(String.class).del(key(id)).replaceWithVoid(), UNI_VOID)
				.onFailure().recoverWithNull();
	}

	// While the circuit is open every call fails fast; the state change is logged once instead of one line per request.
	private static void logFailure(String operation, ProductId id, Throwable e) {
		if (!(e instanceof CircuitBreakerOpenException)) {
			log.warnf("Cache %s failed for product %s: %s", operation, id, e);
		}
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

package com.mango.products.adapters.out.redis;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;
import org.jboss.logging.Logger;

import com.mango.products.application.port.out.ProductCache;
import com.mango.products.domain.Product;
import com.mango.products.domain.ProductId;

import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.value.ReactiveValueCommands;
import io.smallrye.faulttolerance.api.Guard;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.util.TypeLiteral;

public class RedisProductCache implements ProductCache {

	private static final Logger log = Logger.getLogger(RedisProductCache.class);

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
	private final CachedProductCodec codec;
	private final String ttlSeconds;
	private final CacheMetrics metrics;

	private final Guard redisCircuit = Guard.create()
			.withDescription("redis-product-cache")
			.withCircuitBreaker()
				.requestVolumeThreshold(20)
				.failureRatio(0.5)
				.delay(5, ChronoUnit.SECONDS)
				.onStateChange(state -> log.warnf("Redis cache circuit is now %s", state))
				.done()
			.build();

	public RedisProductCache(ReactiveRedisDataSource redis, CachedProductCodec codec, Duration ttl, CacheMetrics metrics) {
		this.redis = redis;
		this.values = redis.value(String.class);
		this.codec = codec;
		this.ttlSeconds = Long.toString(ttl.toSeconds());
		this.metrics = metrics;
	}

	@Override
	public Uni<Product> get(ProductId id) {
		return redisCircuit.get(() -> values.get(key(id)), UNI_STRING)
				.onItem().invoke(value -> metrics.read(value != null))
				.onItem().ifNotNull().transform(codec::decode)
				.onFailure().recoverWithItem(e -> {
					metrics.readFailed();
					logFailure("read", id, e);
					return null;
				});
	}

	@Override
	public Uni<Void> put(Product product) {
		return redisCircuit.get(() -> Uni.createFrom().item(() -> codec.encode(product))
						.chain(value -> redis.execute("EVAL", PUT_IF_NEWER, "1", key(product.id()), value,
								Long.toString(product.version()), ttlSeconds))
						.replaceWithVoid(), UNI_VOID)
				.onFailure().call(e -> evict(product.id()))
				.onFailure().recoverWithItem(e -> {
					metrics.writeFailed();
					logFailure("write", product.id(), e);
					return null;
				});
	}

	private Uni<Void> evict(ProductId id) {
		return redisCircuit.get(() -> redis.key(String.class).del(key(id)).replaceWithVoid(), UNI_VOID)
				.onFailure().recoverWithNull();
	}

	private static void logFailure(String operation, ProductId id, Throwable e) {
		if (!(e instanceof CircuitBreakerOpenException)) {
			log.warnf("Cache %s failed for product %s: %s", operation, id, e);
		}
	}

	private static String key(ProductId id) {
		return "product:" + id.value();
	}

}

package com.mango.products.config;

import java.time.Duration;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mango.products.adapters.out.nearcache.NearProductCache;
import com.mango.products.adapters.out.nocache.DisabledProductCache;
import com.mango.products.adapters.out.redis.RedisProductCache;
import com.mango.products.application.port.out.ProductCache;

import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;

// Picks the cache at startup, so Redis can be switched on per run with PRODUCTS_CACHE_ENABLED.
@ApplicationScoped
class CacheConfiguration {

	@Produces
	@ApplicationScoped
	ProductCache productCache(@ConfigProperty(name = "products.cache.enabled") boolean enabled,
			@ConfigProperty(name = "products.cache.ttl") Duration ttl,
			@ConfigProperty(name = "products.cache.local-ttl") Duration localTtl,
			@ConfigProperty(name = "products.cache.local-max-entries") long localMaxEntries,
			Instance<ReactiveRedisDataSource> redis, ObjectMapper json) {
		if (!enabled) {
			return new DisabledProductCache();
		}
		return new NearProductCache(new RedisProductCache(redis.get(), json, ttl), localTtl, localMaxEntries);
	}

}

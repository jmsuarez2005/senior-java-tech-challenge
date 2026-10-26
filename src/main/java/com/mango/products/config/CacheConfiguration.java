package com.mango.products.config;

import java.time.Duration;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mango.products.adapters.out.nearcache.NearProductCache;
import com.mango.products.adapters.out.nocache.DisabledProductCache;
import com.mango.products.adapters.out.redis.RedisProductCache;
import com.mango.products.application.port.out.ProductCache;

import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;

// Picks the caches at startup: Redis (PRODUCTS_CACHE_ENABLED) and, in front of it, the in-process copy
// (PRODUCTS_LOCAL_CACHE_ENABLED), which trades cross-instance consistency for speed; see the README.
@ApplicationScoped
class CacheConfiguration {

	@Produces
	@ApplicationScoped
	ProductCache productCache(@ConfigProperty(name = "products.cache.enabled") boolean enabled,
			@ConfigProperty(name = "products.cache.ttl") Duration ttl,
			@ConfigProperty(name = "products.cache.local.enabled") boolean localEnabled,
			@ConfigProperty(name = "products.cache.local.ttl") Duration localTtl,
			@ConfigProperty(name = "products.cache.local.max-entries") long localMaxEntries,
			Instance<ReactiveRedisDataSource> redis, ObjectMapper json, MeterRegistry meters) {
		if (!enabled) {
			return new DisabledProductCache();
		}
		ProductCache shared = new RedisProductCache(redis.get(), json, ttl, meters);
		return localEnabled ? new NearProductCache(shared, localTtl, localMaxEntries) : shared;
	}

}

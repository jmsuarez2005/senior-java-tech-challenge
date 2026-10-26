package com.mango.products.adapters.out.redis;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

public class CacheMetrics {

	private final Counter hits;
	private final Counter misses;
	private final Counter readErrors;
	private final Counter writeErrors;

	public CacheMetrics(MeterRegistry meters) {
		this.hits = meters.counter("products.cache.reads", "result", "hit");
		this.misses = meters.counter("products.cache.reads", "result", "miss");
		this.readErrors = meters.counter("products.cache.reads", "result", "error");
		this.writeErrors = meters.counter("products.cache.writes", "result", "error");
	}

	void read(boolean hit) {
		(hit ? hits : misses).increment();
	}

	void readFailed() {
		readErrors.increment();
	}

	void writeFailed() {
		writeErrors.increment();
	}

}

package com.mango.products.adapters.in.web;

import java.util.concurrent.ThreadLocalRandom;

import org.jboss.logging.MDC;

import io.vertx.ext.web.Router;
import jakarta.enterprise.event.Observes;

public class RequestIdFilter {

	static final String HEADER = "X-Request-Id";
	private static final String MDC_KEY = "requestId";

	void register(@Observes Router router) {
		router.route().order(Integer.MIN_VALUE).handler(context -> {
			String id = context.request().getHeader(HEADER);
			if (id == null || id.isBlank()) {
				id = Long.toHexString(ThreadLocalRandom.current().nextLong());
			}
			MDC.put(MDC_KEY, id);
			context.response().putHeader(HEADER, id);
			context.next();
		});
	}

}

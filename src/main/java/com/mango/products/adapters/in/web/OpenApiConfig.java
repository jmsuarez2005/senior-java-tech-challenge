package com.mango.products.adapters.in.web;

import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(info = @Info(title = "Products API", version = "1.0",
		description = "Products with historical prices. Validity periods are closed intervals."))
class OpenApiConfig {
}

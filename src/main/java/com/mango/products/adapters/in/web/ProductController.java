package com.mango.products.adapters.in.web;

import java.net.URI;
import java.time.LocalDate;
import java.util.Currency;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.mango.products.adapters.in.web.dto.AddPriceRequest;
import com.mango.products.adapters.in.web.dto.CreateProductRequest;
import com.mango.products.adapters.in.web.dto.PriceHistoryResponse;
import com.mango.products.adapters.in.web.dto.PriceResponse;
import com.mango.products.adapters.in.web.dto.PriceValueResponse;
import com.mango.products.adapters.in.web.dto.ProductResponse;
import com.mango.products.application.AddPrice;
import com.mango.products.application.CreateProduct;
import com.mango.products.application.FindPriceInForce;
import com.mango.products.application.FindProduct;
import com.mango.products.domain.ProductId;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/products")
@Tag(name = "Products", description = "Products and their price history")
class ProductController {

	private static final String DEFAULT_CURRENCY = "EUR";
	private static final Currency EUR = Currency.getInstance(DEFAULT_CURRENCY);

	private final CreateProduct createProduct;
	private final FindProduct findProduct;
	private final AddPrice addPrice;
	private final FindPriceInForce findPriceInForce;

	ProductController(CreateProduct createProduct, FindProduct findProduct, AddPrice addPrice,
			FindPriceInForce findPriceInForce) {
		this.createProduct = createProduct;
		this.findProduct = findProduct;
		this.addPrice = addPrice;
		this.findPriceInForce = findPriceInForce;
	}

	@PostMapping
	@Operation(summary = "Create a product")
	@ApiResponse(responseCode = "201", description = "Created; Location points to the new product")
	@ApiResponse(responseCode = "400", description = "Invalid name or description")
	Mono<ResponseEntity<ProductResponse>> create(@Valid @RequestBody CreateProductRequest request) {
		return createProduct.execute(request.name(), request.description())
				.map(product -> ResponseEntity.created(URI.create("/products/" + product.id()))
						.body(ProductResponse.from(product)));
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get a product")
	@ApiResponse(responseCode = "404", description = "Unknown product")
	Mono<ProductResponse> get(@PathVariable String id) {
		return findProduct.execute(new ProductId(id)).map(ProductResponse::from);
	}

	@PostMapping("/{id}/prices")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Add a price",
			description = "Validity is a closed interval: both initDate and endDate are included. "
					+ "Rejected if it overlaps another price of the same product and currency.")
	@ApiResponse(responseCode = "400", description = "Invalid value or dates")
	@ApiResponse(responseCode = "404", description = "Unknown product")
	@ApiResponse(responseCode = "409", description = "Overlaps an existing price in the same currency")
	Mono<PriceResponse> addPrice(@PathVariable String id, @Valid @RequestBody AddPriceRequest request) {
		return Mono.fromCallable(() -> request.toPrice(EUR))
				.flatMap(price -> addPrice.execute(new ProductId(id), price))
				.map(PriceResponse::from);
	}

	@GetMapping(path = "/{id}/prices", params = "date")
	@Operation(summary = "Get the price in force on a date",
			description = "Kept for compatibility; GET /products/{id}/price is the preferred form.")
	@ApiResponse(responseCode = "404", description = "Unknown product or no price on that date")
	Mono<PriceValueResponse> priceOn(@PathVariable String id,
			@RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate date,
			@Parameter(description = "ISO 4217 code") @RequestParam(defaultValue = DEFAULT_CURRENCY) Currency currency) {
		return findPriceInForce.execute(new ProductId(id), date, currency).map(PriceValueResponse::from);
	}

	@GetMapping("/{id}/price")
	@Operation(summary = "Get the price in force on a date")
	@ApiResponse(responseCode = "404", description = "Unknown product or no price on that date")
	Mono<PriceValueResponse> price(@PathVariable String id,
			@RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate date,
			@Parameter(description = "ISO 4217 code") @RequestParam(defaultValue = DEFAULT_CURRENCY) Currency currency) {
		return priceOn(id, date, currency);
	}

	@GetMapping(path = "/{id}/prices", params = "!date")
	@Operation(summary = "Get the price history", description = "Prices in one currency, ordered by initDate.")
	@ApiResponse(responseCode = "404", description = "Unknown product")
	Mono<PriceHistoryResponse> history(@PathVariable String id,
			@Parameter(description = "ISO 4217 code") @RequestParam(defaultValue = DEFAULT_CURRENCY) Currency currency) {
		return findProduct.execute(new ProductId(id)).map(product -> PriceHistoryResponse.from(product, currency));
	}

}

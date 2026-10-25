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
import com.mango.products.adapters.in.web.dto.ApiDefaults;
import com.mango.products.adapters.in.web.dto.CreateProductRequest;
import com.mango.products.adapters.in.web.dto.PriceHistoryResponse;
import com.mango.products.adapters.in.web.dto.PriceResponse;
import com.mango.products.adapters.in.web.dto.PriceValueResponse;
import com.mango.products.adapters.in.web.dto.ProductResponse;
import com.mango.products.application.AddPrice;
import com.mango.products.application.CreateProduct;
import com.mango.products.application.FindPriceInForce;
import com.mango.products.application.FindProduct;
import com.mango.products.domain.Product;
import com.mango.products.domain.ProductId;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/products")
@Tag(name = "Products", description = "Products and their price history")
class ProductController {

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
	ResponseEntity<ProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
		Product product = createProduct.execute(request.name(), request.description());
		return ResponseEntity.created(URI.create("/products/" + product.id())).body(ProductResponse.from(product));
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get a product")
	@ApiResponse(responseCode = "404", description = "Unknown product")
	ProductResponse get(@PathVariable String id) {
		return ProductResponse.from(findProduct.execute(new ProductId(id)));
	}

	@PostMapping("/{id}/prices")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Add a price",
			description = "Validity is a closed interval: both initDate and endDate are included. "
					+ "Rejected if it overlaps another price of the same product and currency.")
	@ApiResponse(responseCode = "400", description = "Invalid value or dates")
	@ApiResponse(responseCode = "404", description = "Unknown product")
	@ApiResponse(responseCode = "409", description = "Overlaps an existing price in the same currency")
	PriceResponse addPrice(@PathVariable String id, @Valid @RequestBody AddPriceRequest request) {
		return PriceResponse.from(addPrice.execute(new ProductId(id), request.toPrice()));
	}

	@GetMapping(path = "/{id}/prices", params = "date")
	@Operation(summary = "Get the price in force on a date",
			description = "Kept for compatibility; GET /products/{id}/price is the preferred form.")
	@ApiResponse(responseCode = "404", description = "Unknown product or no price on that date")
	PriceValueResponse priceOn(@PathVariable String id,
			@RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate date,
			@Parameter(description = "ISO 4217 code") @RequestParam(defaultValue = ApiDefaults.CURRENCY_CODE) Currency currency) {
		return PriceValueResponse.from(findPriceInForce.execute(new ProductId(id), date, currency));
	}

	@GetMapping("/{id}/price")
	@Operation(summary = "Get the price in force on a date")
	@ApiResponse(responseCode = "404", description = "Unknown product or no price on that date")
	PriceValueResponse price(@PathVariable String id,
			@RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate date,
			@Parameter(description = "ISO 4217 code") @RequestParam(defaultValue = ApiDefaults.CURRENCY_CODE) Currency currency) {
		return priceOn(id, date, currency);
	}

	@GetMapping(path = "/{id}/prices", params = "!date")
	@Operation(summary = "Get the price history", description = "Prices in one currency, ordered by initDate.")
	@ApiResponse(responseCode = "404", description = "Unknown product")
	PriceHistoryResponse history(@PathVariable String id,
			@Parameter(description = "ISO 4217 code") @RequestParam(defaultValue = ApiDefaults.CURRENCY_CODE) Currency currency) {
		return PriceHistoryResponse.from(findProduct.execute(new ProductId(id)), currency);
	}

}

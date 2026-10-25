package com.mango.products.adapters.in.web;

import java.net.URI;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.ResponseStatus;

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
import com.mango.products.domain.ProductId;

import io.smallrye.mutiny.Uni;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/products")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Products", description = "Products and their price history")
public class ProductResource {

	private final CreateProduct createProduct;
	private final FindProduct findProduct;
	private final AddPrice addPrice;
	private final FindPriceInForce findPriceInForce;

	public ProductResource(CreateProduct createProduct, FindProduct findProduct, AddPrice addPrice,
			FindPriceInForce findPriceInForce) {
		this.createProduct = createProduct;
		this.findProduct = findProduct;
		this.addPrice = addPrice;
		this.findPriceInForce = findPriceInForce;
	}

	@POST
	@Operation(summary = "Create a product")
	@APIResponse(responseCode = "201", description = "Created; Location points to the new product")
	@APIResponse(responseCode = "400", description = "Invalid name or description")
	public Uni<Response> create(@Valid CreateProductRequest request) {
		return createProduct.execute(request.name(), request.description())
				.map(product -> Response.created(URI.create("/products/" + product.id()))
						.entity(ProductResponse.from(product))
						.build());
	}

	@GET
	@Path("/{id}")
	@Operation(summary = "Get a product")
	@APIResponse(responseCode = "404", description = "Unknown product")
	public Uni<ProductResponse> get(@PathParam("id") String id) {
		return findProduct.execute(new ProductId(id)).map(ProductResponse::from);
	}

	@POST
	@Path("/{id}/prices")
	@ResponseStatus(201)
	@Operation(summary = "Add a price",
			description = "Validity is a closed interval: both initDate and endDate are included. "
					+ "Rejected if it overlaps another price of the same product and currency.")
	@APIResponse(responseCode = "400", description = "Invalid value or dates")
	@APIResponse(responseCode = "404", description = "Unknown product")
	@APIResponse(responseCode = "409", description = "Overlaps an existing price in the same currency")
	public Uni<PriceResponse> addPrice(@PathParam("id") String id, @Valid AddPriceRequest request) {
		return Uni.createFrom().item(request::toPrice)
				.chain(price -> addPrice.execute(new ProductId(id), price))
				.map(PriceResponse::from);
	}

	@GET
	@Path("/{id}/prices")
	@Operation(summary = "Get the price history, or the price in force when date is given",
			description = "Without date: prices in one currency ordered by initDate. With date: {\"value\": ...}. "
					+ "Kept for compatibility; GET /products/{id}/price is the preferred form for a single price.")
	@APIResponse(responseCode = "404", description = "Unknown product or no price on that date")
	public Uni<?> prices(@PathParam("id") String id, @QueryParam("date") String date,
			@Parameter(description = "ISO 4217 code") @QueryParam("currency") @DefaultValue(ApiDefaults.CURRENCY_CODE) String currency) {
		if (date != null) {
			return price(id, date, currency);
		}
		return Uni.createFrom().item(() -> RequestParams.currency(currency))
				.chain(code -> findProduct.execute(new ProductId(id)).map(product -> PriceHistoryResponse.from(product, code)));
	}

	@GET
	@Path("/{id}/price")
	@Operation(summary = "Get the price in force on a date")
	@APIResponse(responseCode = "404", description = "Unknown product or no price on that date")
	public Uni<PriceValueResponse> price(@PathParam("id") String id, @QueryParam("date") String date,
			@Parameter(description = "ISO 4217 code") @QueryParam("currency") @DefaultValue(ApiDefaults.CURRENCY_CODE) String currency) {
		return Uni.createFrom().item(() -> RequestParams.date(date))
				.chain(day -> findPriceInForce.execute(new ProductId(id), day, RequestParams.currency(currency)))
				.map(PriceValueResponse::from);
	}

}

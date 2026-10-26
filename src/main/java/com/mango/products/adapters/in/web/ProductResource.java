package com.mango.products.adapters.in.web;

import java.net.URI;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.OAuthFlow;
import org.eclipse.microprofile.openapi.annotations.security.OAuthFlows;
import org.eclipse.microprofile.openapi.annotations.security.OAuthScope;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.ResponseStatus;

import com.mango.products.adapters.in.web.dto.AddPriceRequest;
import com.mango.products.adapters.in.web.dto.ApiDefaults;
import com.mango.products.adapters.in.web.dto.CreateProductRequest;
import com.mango.products.adapters.in.web.dto.HistoryQuery;
import com.mango.products.adapters.in.web.dto.PriceHistoryResponse;
import com.mango.products.adapters.in.web.dto.PriceResponse;
import com.mango.products.adapters.in.web.dto.PriceValueResponse;
import com.mango.products.adapters.in.web.dto.ProductResponse;
import com.mango.products.adapters.in.web.dto.ReplacePriceRequest;
import com.mango.products.application.AddPrice;
import com.mango.products.application.CreateProduct;
import com.mango.products.application.FindPriceInForce;
import com.mango.products.application.FindProduct;
import com.mango.products.application.RemovePrice;
import com.mango.products.application.ReplacePrice;
import com.mango.products.domain.DateRange;
import com.mango.products.domain.Money;
import com.mango.products.domain.Price;
import com.mango.products.domain.Product;
import com.mango.products.domain.ProductId;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

@Path("/products")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Products", description = "Products and their price history")
@SecurityScheme(securitySchemeName = Scopes.SCHEME, type = SecuritySchemeType.OAUTH2,
		description = "OAuth 2.0 client credentials against the bundled Keycloak (see README)",
		flows = @OAuthFlows(clientCredentials = @OAuthFlow(tokenUrl = Scopes.TOKEN_URL,
				scopes = @OAuthScope(name = Scopes.WRITE, description = "Create, change and delete prices"))))
public class ProductResource {

	private final CreateProduct createProduct;
	private final FindProduct findProduct;
	private final AddPrice addPrice;
	private final FindPriceInForce findPriceInForce;
	private final ReplacePrice replacePrice;
	private final RemovePrice removePrice;

	public ProductResource(CreateProduct createProduct, FindProduct findProduct, AddPrice addPrice,
			FindPriceInForce findPriceInForce, ReplacePrice replacePrice, RemovePrice removePrice) {
		this.createProduct = createProduct;
		this.findProduct = findProduct;
		this.addPrice = addPrice;
		this.findPriceInForce = findPriceInForce;
		this.replacePrice = replacePrice;
		this.removePrice = removePrice;
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
			description = "Without date: prices in one currency, filtered, sorted and optionally paged; the body keeps the "
					+ "statement's shape and paging goes in the X-Total-Count and Link headers. With date: {\"value\": ...}, "
					+ "kept for compatibility; GET /products/{id}/price is the preferred form for a single price.")
	@APIResponse(responseCode = "400", description = "Invalid date, currency, window, sort or paging")
	@APIResponse(responseCode = "404", description = "Unknown product or no price on that date")
	public Uni<Response> prices(@PathParam("id") String id, @QueryParam("date") String date,
			@Parameter(description = "ISO 4217 code") @QueryParam("currency") @DefaultValue(ApiDefaults.CURRENCY_CODE) String currency,
			@Parameter(description = "Only prices in force on or after this day") @QueryParam("from") String from,
			@Parameter(description = "Only prices in force on or before this day") @QueryParam("to") String to,
			@Parameter(description = "initDate,asc or initDate,desc") @QueryParam("sort") @DefaultValue("initDate,asc") String sort,
			@Parameter(description = "Zero-based page number") @QueryParam("page") @DefaultValue("0") int page,
			@Parameter(description = "Page size (1 to 500); omit to get the whole history") @QueryParam("size") @DefaultValue("0") int size,
			@Context UriInfo uri) {
		if (date != null) {
			return price(id, date, currency).map(value -> Response.ok(value).build());
		}
		return Uni.createFrom().item(() -> RequestParams.historyQuery(currency, from, to, sort, page, size))
				.chain(query -> findProduct.execute(new ProductId(id)).map(product -> history(product, query, uri)));
	}

	private static Response history(Product product, HistoryQuery query, UriInfo uri) {
		List<Price> matching = query.matching(product);
		Response.ResponseBuilder response = Response.ok(PriceHistoryResponse.from(product, query.pageOf(matching)));
		if (query.paginated()) {
			response.header("X-Total-Count", matching.size());
			if (query.page() > 0) {
				response.link(uri.getRequestUriBuilder().replaceQueryParam("page", query.page() - 1).build(), "prev");
			}
			if (query.hasNextPage(matching.size())) {
				response.link(uri.getRequestUriBuilder().replaceQueryParam("page", query.page() + 1).build(), "next");
			}
		}
		return response.build();
	}

	@PUT
	@Path("/{id}/prices/{currency}/{initDate}")
	@RolesAllowed(Scopes.WRITE)
	@SecurityRequirement(name = Scopes.SCHEME)
	@Operation(summary = "Change a price's value or end date",
			description = "The price is identified by its currency and initDate. Rejected if the new period overlaps "
					+ "another price of the same currency. Requires an OAuth 2.0 token with the products:write scope.")
	@APIResponse(responseCode = "200", description = "Price replaced")
	@APIResponse(responseCode = "400", description = "Invalid value or dates")
	@APIResponse(responseCode = "401", description = "Missing or invalid token")
	@APIResponse(responseCode = "403", description = "Token without the products:write scope")
	@APIResponse(responseCode = "404", description = "Unknown product or price")
	@APIResponse(responseCode = "409", description = "The new period overlaps another price in the same currency")
	public Uni<PriceResponse> replacePrice(@PathParam("id") String id, @PathParam("currency") String currency,
			@PathParam("initDate") String initDate, @Valid ReplacePriceRequest request) {
		return Uni.createFrom().item(() -> {
			Currency code = RequestParams.currency(currency);
			LocalDate start = RequestParams.requiredDate("initDate", initDate);
			return new Price(Money.of(request.value(), code), new DateRange(start, request.endDate()));
		})
				.chain(price -> replacePrice.execute(new ProductId(id), price))
				.map(PriceResponse::from);
	}

	@DELETE
	@Path("/{id}/prices/{currency}/{initDate}")
	@RolesAllowed(Scopes.WRITE)
	@SecurityRequirement(name = Scopes.SCHEME)
	@ResponseStatus(204)
	@Operation(summary = "Delete a price", description = "Requires an OAuth 2.0 token with the products:write scope.")
	@APIResponse(responseCode = "204", description = "Price deleted")
	@APIResponse(responseCode = "401", description = "Missing or invalid token")
	@APIResponse(responseCode = "403", description = "Token without the products:write scope")
	@APIResponse(responseCode = "404", description = "Unknown product or price")
	public Uni<Void> removePrice(@PathParam("id") String id, @PathParam("currency") String currency,
			@PathParam("initDate") String initDate) {
		return Uni.createFrom().item(() -> RequestParams.currency(currency))
				.chain(code -> removePrice.execute(new ProductId(id), code, RequestParams.requiredDate("initDate", initDate)));
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

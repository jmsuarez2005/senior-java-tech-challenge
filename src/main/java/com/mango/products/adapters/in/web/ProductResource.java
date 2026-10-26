package com.mango.products.adapters.in.web;

import java.time.LocalDate;
import java.util.Currency;
import java.util.List;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.headers.Header;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
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

@Path("/v1/products")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Products", description = "Products and their price history")
@SecurityScheme(securitySchemeName = Scopes.SCHEME, type = SecuritySchemeType.OAUTH2,
		description = "OAuth 2.0 client credentials against the bundled Keycloak. Use client products-writer with secret "
				+ "products-writer-secret and the products:write scope.",
		flows = @OAuthFlows(clientCredentials = @OAuthFlow(tokenUrl = Scopes.TOKEN_URL,
				scopes = @OAuthScope(name = Scopes.WRITE, description = "Create, change and delete prices"))))
public class ProductResource {

	private static final String ID = "Product id, as returned when the product was created";
	private static final String CURRENCY = "ISO 4217 currency code";
	private static final String JSON = MediaType.APPLICATION_JSON;

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
	@Operation(summary = "Create a product", description = "Creates a product without prices.")
	@APIResponse(responseCode = "201", description = "Product created",
			headers = @Header(name = "Location", description = "URL of the new product", schema = @Schema(type = SchemaType.STRING)),
			content = @Content(mediaType = JSON, schema = @Schema(implementation = ProductResponse.class)))
	@APIResponse(responseCode = "400", ref = "BadRequest")
	public Uni<Response> create(@Valid CreateProductRequest request, @Context UriInfo uri) {
		return createProduct.execute(request.name(), request.description())
				.map(product -> Response.created(uri.getAbsolutePathBuilder().path(product.id().value()).build())
						.entity(ProductResponse.from(product))
						.build());
	}

	@GET
	@Path("/{id}")
	@Operation(summary = "Get a product", description = "Returns the product without its prices.")
	@APIResponse(responseCode = "200", description = "The product",
			content = @Content(mediaType = JSON, schema = @Schema(implementation = ProductResponse.class)))
	@APIResponse(responseCode = "404", ref = "NotFound")
	public Uni<ProductResponse> get(@Parameter(description = ID) @PathParam("id") String id) {
		return findProduct.execute(new ProductId(id)).map(ProductResponse::from);
	}

	@POST
	@Path("/{id}/prices")
	@ResponseStatus(201)
	@Operation(summary = "Add a price",
			description = "Validity is a closed interval: both initDate and endDate are included, and a null endDate means "
					+ "until further notice. Rejected if it overlaps another price of the same product and currency, "
					+ "even by a single day.")
	@APIResponse(responseCode = "201", description = "Price added",
			content = @Content(mediaType = JSON, schema = @Schema(implementation = PriceResponse.class)))
	@APIResponse(responseCode = "400", ref = "BadRequest")
	@APIResponse(responseCode = "404", ref = "NotFound")
	@APIResponse(responseCode = "409", ref = "Conflict")
	public Uni<PriceResponse> addPrice(@Parameter(description = ID) @PathParam("id") String id,
			@Valid AddPriceRequest request) {
		return Uni.createFrom().item(request::toPrice)
				.chain(price -> addPrice.execute(new ProductId(id), price))
				.map(PriceResponse::from);
	}

	@GET
	@Path("/{id}/prices")
	@Operation(summary = "Get the price history, or the price in force on a date",
			description = "Without `date`: the prices in one currency, optionally filtered by a window, sorted and paged. "
					+ "The body keeps the same shape with or without paging; paging travels in the `X-Total-Count` and "
					+ "`Link` headers. With `date`: `{\"value\": ...}`, kept for compatibility with the statement; "
					+ "`GET /v1/products/{id}/price` is the preferred form for a single price.")
	@APIResponse(responseCode = "200", description = "The history, or the price in force when date is given",
			headers = {
					@Header(name = "X-Total-Count", description = "Prices matching the filters, when paging", schema = @Schema(type = SchemaType.INTEGER)),
					@Header(name = "Link", description = "RFC 8288 links with rel=\"prev\" and rel=\"next\", when paging", schema = @Schema(type = SchemaType.STRING)) },
			content = @Content(mediaType = JSON,
					schema = @Schema(oneOf = { PriceHistoryResponse.class, PriceValueResponse.class })))
	@APIResponse(responseCode = "400", ref = "BadRequest")
	@APIResponse(responseCode = "404", ref = "NotFound")
	public Uni<Response> prices(@Parameter(description = ID) @PathParam("id") String id,
			@Parameter(description = "Day to get the price in force for (yyyy-MM-dd); switches the response to {value}",
					example = "2024-04-15") @QueryParam("date") String date,
			@Parameter(description = CURRENCY) @QueryParam("currency") @DefaultValue(ApiDefaults.CURRENCY_CODE) String currency,
			@Parameter(description = "Only prices in force on or after this day (yyyy-MM-dd)") @QueryParam("from") String from,
			@Parameter(description = "Only prices in force on or before this day (yyyy-MM-dd)") @QueryParam("to") String to,
			@Parameter(description = "initDate,asc or initDate,desc") @QueryParam("sort") @DefaultValue("initDate,asc") String sort,
			@Parameter(description = "Zero-based page number") @QueryParam("page") @DefaultValue("0") int page,
			@Parameter(description = "Page size, 1 to 500; leave out to get the whole history") @QueryParam("size") @DefaultValue("0") int size,
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

	@GET
	@Path("/{id}/price")
	@Operation(summary = "Get the price in force on a date",
			description = "The single price whose closed validity period contains the day, in the given currency.")
	@APIResponse(responseCode = "200", description = "The price in force",
			content = @Content(mediaType = JSON, schema = @Schema(implementation = PriceValueResponse.class)))
	@APIResponse(responseCode = "400", ref = "BadRequest")
	@APIResponse(responseCode = "404", ref = "NotFound")
	public Uni<PriceValueResponse> price(@Parameter(description = ID) @PathParam("id") String id,
			@Parameter(description = "Day (yyyy-MM-dd)", required = true, example = "2024-04-15") @QueryParam("date") String date,
			@Parameter(description = CURRENCY) @QueryParam("currency") @DefaultValue(ApiDefaults.CURRENCY_CODE) String currency) {
		return Uni.createFrom().item(() -> RequestParams.date(date))
				.chain(day -> findPriceInForce.execute(new ProductId(id), day, RequestParams.currency(currency)))
				.map(PriceValueResponse::from);
	}

	@PUT
	@Path("/{id}/prices/{currency}/{initDate}")
	@RolesAllowed(Scopes.WRITE)
	@SecurityRequirement(name = Scopes.SCHEME, scopes = Scopes.WRITE)
	@Operation(summary = "Change a price's value or end date",
			description = "The price is identified by its currency and initDate, which never change. Rejected if the new "
					+ "period overlaps another price of the same currency. Requires a token with the products:write scope.")
	@APIResponse(responseCode = "200", description = "Price replaced",
			content = @Content(mediaType = JSON, schema = @Schema(implementation = PriceResponse.class)))
	@APIResponse(responseCode = "400", ref = "BadRequest")
	@APIResponse(responseCode = "401", ref = "Unauthorized")
	@APIResponse(responseCode = "403", ref = "Forbidden")
	@APIResponse(responseCode = "404", ref = "NotFound")
	@APIResponse(responseCode = "409", ref = "Conflict")
	public Uni<PriceResponse> replacePrice(@Parameter(description = ID) @PathParam("id") String id,
			@Parameter(description = CURRENCY, example = "EUR") @PathParam("currency") String currency,
			@Parameter(description = "First day of the price (yyyy-MM-dd)", example = "2024-01-01") @PathParam("initDate") String initDate,
			@Valid ReplacePriceRequest request) {
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
	@SecurityRequirement(name = Scopes.SCHEME, scopes = Scopes.WRITE)
	@ResponseStatus(204)
	@Operation(summary = "Delete a price", description = "Requires a token with the products:write scope.")
	@APIResponse(responseCode = "204", description = "Price deleted")
	@APIResponse(responseCode = "400", ref = "BadRequest")
	@APIResponse(responseCode = "401", ref = "Unauthorized")
	@APIResponse(responseCode = "403", ref = "Forbidden")
	@APIResponse(responseCode = "404", ref = "NotFound")
	public Uni<Void> removePrice(@Parameter(description = ID) @PathParam("id") String id,
			@Parameter(description = CURRENCY, example = "EUR") @PathParam("currency") String currency,
			@Parameter(description = "First day of the price (yyyy-MM-dd)", example = "2024-01-01") @PathParam("initDate") String initDate) {
		return Uni.createFrom().item(() -> RequestParams.currency(currency))
				.chain(code -> removePrice.execute(new ProductId(id), code, RequestParams.requiredDate("initDate", initDate)));
	}

}

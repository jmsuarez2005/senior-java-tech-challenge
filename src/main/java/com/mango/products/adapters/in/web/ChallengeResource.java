package com.mango.products.adapters.in.web;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.headers.Header;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
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

import io.smallrye.mutiny.Uni;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
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
@Tag(name = "Challenge endpoints", description = "The four endpoints of the statement, unchanged")
public class ChallengeResource {

	private static final String ID = "Product id, as returned when the product was created";
	private static final String JSON = MediaType.APPLICATION_JSON;

	private final ProductEndpoints endpoints;

	public ChallengeResource(ProductEndpoints endpoints) {
		this.endpoints = endpoints;
	}

	@POST
	@Operation(summary = "Create a product")
	@APIResponse(responseCode = "201", description = "Product created",
			headers = @Header(name = "Location", description = "URL of the new product", schema = @Schema(type = SchemaType.STRING)),
			content = @Content(mediaType = JSON, schema = @Schema(implementation = ProductResponse.class)))
	@APIResponse(responseCode = "400", ref = "BadRequest")
	public Uni<Response> create(@NotNull(message = ProductEndpoints.REQUIRED_BODY) @Valid CreateProductRequest body, @Context UriInfo uri) {
		return endpoints.create(body, uri);
	}

	@POST
	@Path("/{id}/prices")
	@ResponseStatus(201)
	@Operation(summary = "Add a price to a product",
			description = "No overlap with other prices of the product; endDate may be null; initDate must be before endDate.")
	@APIResponse(responseCode = "201", description = "Price added",
			content = @Content(mediaType = JSON, schema = @Schema(implementation = PriceResponse.class)))
	@APIResponse(responseCode = "400", ref = "BadRequest")
	@APIResponse(responseCode = "404", ref = "NotFound")
	@APIResponse(responseCode = "409", ref = "Conflict")
	public Uni<PriceResponse> addPrice(@Parameter(description = ID) @PathParam("id") String id,
			@NotNull(message = ProductEndpoints.REQUIRED_BODY) @Valid AddPriceRequest body) {
		return endpoints.addPrice(id, body);
	}

	@GET
	@Path("/{id}/prices")
	@Operation(summary = "Get the price in force on a date, or the full price history",
			description = "With `date`: `{\"value\": 99.99}`. Without it: `{name, description, prices}` ordered by initDate.")
	@APIResponse(responseCode = "200", description = "The price in force, or the history",
			content = @Content(mediaType = JSON,
					schema = @Schema(oneOf = { PriceValueResponse.class, PriceHistoryResponse.class })))
	@APIResponse(responseCode = "400", ref = "BadRequest")
	@APIResponse(responseCode = "404", ref = "NotFound")
	public Uni<Response> prices(@Parameter(description = ID) @PathParam("id") String id,
			@Parameter(description = "Day (yyyy-MM-dd)", example = "2024-04-15") @QueryParam("date") String date,
			@Parameter(description = "ISO 4217 currency code (multi-currency bonus)") @QueryParam("currency")
			@DefaultValue(ApiDefaults.CURRENCY_CODE) String currency,
			@Context UriInfo uri) {
		return endpoints.prices(id, date, currency, null, null, "initDate,asc", 0, 0, uri);
	}

}

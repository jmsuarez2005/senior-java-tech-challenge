package com.mango.products.adapters.in.web;

import java.time.LocalDate;
import java.util.Currency;
import java.util.List;

import com.mango.products.adapters.in.web.dto.AddPriceRequest;
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
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

// What both resources do once a request is routed and validated. The resources keep only paths, validation and
// documentation, so the statement's endpoints and /v1 share one implementation without calling each other.
@ApplicationScoped
class ProductEndpoints {

	static final String REQUIRED_BODY = "request body is required";

	private final CreateProduct createProduct;
	private final FindProduct findProduct;
	private final AddPrice addPrice;
	private final FindPriceInForce findPriceInForce;
	private final ReplacePrice replacePrice;
	private final RemovePrice removePrice;

	ProductEndpoints(CreateProduct createProduct, FindProduct findProduct, AddPrice addPrice,
			FindPriceInForce findPriceInForce, ReplacePrice replacePrice, RemovePrice removePrice) {
		this.createProduct = createProduct;
		this.findProduct = findProduct;
		this.addPrice = addPrice;
		this.findPriceInForce = findPriceInForce;
		this.replacePrice = replacePrice;
		this.removePrice = removePrice;
	}

	Uni<Response> create(CreateProductRequest request, UriInfo uri) {
		return createProduct.execute(request.name(), request.description())
				.map(product -> Response.created(uri.getAbsolutePathBuilder().path(product.id().value()).build())
						.entity(ProductResponse.from(product))
						.build());
	}

	Uni<Response> get(String id) {
		return findProduct.execute(new ProductId(id))
				.map(product -> Response.ok(ProductResponse.from(product)).tag(versionTag(product)).build());
	}

	Uni<PriceResponse> addPrice(String id, AddPriceRequest request) {
		return Uni.createFrom().item(request::toPrice)
				.chain(price -> addPrice.execute(new ProductId(id), price))
				.map(PriceResponse::from);
	}

	Uni<PriceValueResponse> priceInForce(String id, String date, String currency) {
		return Uni.createFrom().item(() -> RequestParams.date(date))
				.chain(day -> findPriceInForce.execute(new ProductId(id), day, RequestParams.currency(currency)))
				.map(PriceValueResponse::from);
	}

	// GET .../prices: the price in force when date is given, otherwise the history.
	Uni<Response> prices(String id, String date, String currency, String from, String to, String sort, int page,
			int size, UriInfo uri) {
		if (date != null) {
			return priceInForce(id, date, currency).map(value -> Response.ok(value).build());
		}
		return Uni.createFrom().item(() -> RequestParams.historyQuery(currency, from, to, sort, page, size))
				.chain(query -> findProduct.execute(new ProductId(id)).map(product -> history(product, query, uri)));
	}

	Uni<Response> replacePrice(String id, String currency, String initDate, String ifMatch, ReplacePriceRequest request) {
		return Uni.createFrom().item(() -> {
			Currency code = RequestParams.currency(currency);
			LocalDate start = RequestParams.requiredDate("initDate", initDate);
			return new Price(Money.of(request.value(), code), new DateRange(start, request.endDate()));
		})
				.chain(price -> replacePrice.execute(new ProductId(id), price, RequestParams.expectedVersion(ifMatch))
						.map(product -> Response.ok(PriceResponse.from(price)).tag(versionTag(product)).build()));
	}

	Uni<Response> removePrice(String id, String currency, String initDate, String ifMatch) {
		return Uni.createFrom().item(() -> RequestParams.currency(currency))
				.chain(code -> removePrice.execute(new ProductId(id), code, RequestParams.requiredDate("initDate", initDate),
						RequestParams.expectedVersion(ifMatch)))
				.map(product -> Response.noContent().tag(versionTag(product)).build());
	}

	private static Response history(Product product, HistoryQuery query, UriInfo uri) {
		List<Price> matching = query.matching(product);
		Response.ResponseBuilder response = Response.ok(PriceHistoryResponse.from(product, query.pageOf(matching)))
				.tag(versionTag(product));
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

	private static EntityTag versionTag(Product product) {
		return new EntityTag(Long.toString(product.version()));
	}

}

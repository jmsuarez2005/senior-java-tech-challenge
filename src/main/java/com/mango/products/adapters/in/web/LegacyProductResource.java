package com.mango.products.adapters.in.web;

import com.mango.products.application.AddPrice;
import com.mango.products.application.CreateProduct;
import com.mango.products.application.FindPriceInForce;
import com.mango.products.application.FindProduct;
import com.mango.products.application.RemovePrice;
import com.mango.products.application.ReplacePrice;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

// The statement's automated tests call /products without a version, so the same endpoints stay mounted there.
// Hidden from Swagger, where /v1 is the documented API.
@Path("/products")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class LegacyProductResource extends ProductResource {

	public LegacyProductResource(CreateProduct createProduct, FindProduct findProduct, AddPrice addPrice,
			FindPriceInForce findPriceInForce, ReplacePrice replacePrice, RemovePrice removePrice) {
		super(createProduct, findProduct, addPrice, findPriceInForce, replacePrice, removePrice);
	}

}

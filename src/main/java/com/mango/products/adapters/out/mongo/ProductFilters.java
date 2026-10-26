package com.mango.products.adapters.out.mongo;

import static com.mango.products.adapters.out.mongo.PriceDocumentMapper.CURRENCY;
import static com.mango.products.adapters.out.mongo.PriceDocumentMapper.END_DATE;
import static com.mango.products.adapters.out.mongo.PriceDocumentMapper.INIT_DATE;
import static com.mango.products.adapters.out.mongo.ProductDocumentMapper.ID;
import static com.mango.products.adapters.out.mongo.ProductDocumentMapper.PRICES;
import static com.mango.products.adapters.out.mongo.ProductDocumentMapper.VERSION;
import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.elemMatch;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.gte;
import static com.mongodb.client.model.Filters.lte;
import static com.mongodb.client.model.Filters.ne;
import static com.mongodb.client.model.Filters.not;

import java.time.LocalDate;
import java.util.Currency;
import java.util.OptionalLong;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import com.mango.products.domain.ProductId;

final class ProductFilters {

	private ProductFilters() {
	}

	static Bson byId(ProductId id) {
		return eq(ID, new ObjectId(id.value()));
	}

	static Bson byId(ProductId id, OptionalLong expectedVersion) {
		return expectedVersion.isPresent() ? and(byId(id), eq(VERSION, expectedVersion.getAsLong())) : byId(id);
	}

	static Bson hasPrice(Currency currency, LocalDate initDate) {
		return elemMatch(PRICES, samePrice(currency, initDate));
	}

	static Bson samePrice(Currency currency, LocalDate initDate) {
		return and(eq(CURRENCY, currency.getCurrencyCode()), eq(INIT_DATE, initDate.toString()));
	}

	static Bson noPriceOverlapping(Document price) {
		return not(elemMatch(PRICES, overlapping(price)));
	}

	static Bson noOtherPriceOverlapping(Document price) {
		return not(elemMatch(PRICES, and(overlapping(price), ne(INIT_DATE, price.getString(INIT_DATE)))));
	}

	static Bson arrayElement(String alias, Document price) {
		return and(eq(alias + "." + CURRENCY, price.getString(CURRENCY)),
				eq(alias + "." + INIT_DATE, price.getString(INIT_DATE)));
	}

	private static Bson overlapping(Document price) {
		return and(eq(CURRENCY, price.getString(CURRENCY)),
				lte(INIT_DATE, price.getString(END_DATE)),
				gte(END_DATE, price.getString(INIT_DATE)));
	}

}

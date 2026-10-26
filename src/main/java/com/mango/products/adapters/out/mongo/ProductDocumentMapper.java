package com.mango.products.adapters.out.mongo;

import java.time.LocalDate;
import java.util.Currency;
import java.util.List;

import org.bson.Document;
import org.bson.types.ObjectId;

import com.mango.products.domain.DateRange;
import com.mango.products.domain.Money;
import com.mango.products.domain.Price;
import com.mango.products.domain.Product;
import com.mango.products.domain.ProductId;

final class ProductDocumentMapper {

	static final String ID = "_id";
	static final String NAME = "name";
	static final String DESCRIPTION = "description";
	static final String PRICES = "prices";
	static final String VERSION = "version";
	static final String AMOUNT_MINOR = "amountMinor";
	static final String CURRENCY = "currency";
	static final String INIT_DATE = "initDate";
	static final String END_DATE = "endDate";

	private ProductDocumentMapper() {
	}

	static Document toDocument(Product product) {
		return new Document(ID, new ObjectId(product.id().value()))
				.append(NAME, product.name())
				.append(DESCRIPTION, product.description())
				.append(PRICES, product.prices().stream().map(ProductDocumentMapper::toDocument).toList())
				.append(VERSION, product.version());
	}

	static Document toDocument(Price price) {
		return new Document(AMOUNT_MINOR, price.money().amountMinor())
				.append(CURRENCY, price.money().currency().getCurrencyCode())
				.append(INIT_DATE, price.period().initDate().toString())
				.append(END_DATE, price.period().endDate().toString());
	}

	static Product toDomain(Document document) {
		List<Price> prices = document.getList(PRICES, Document.class, List.of()).stream()
				.map(ProductDocumentMapper::toPrice)
				.toList();
		return new Product(new ProductId(document.getObjectId(ID).toHexString()), document.getString(NAME),
				document.getString(DESCRIPTION), prices, document.getLong(VERSION));
	}

	private static Price toPrice(Document document) {
		return new Price(new Money(document.getLong(AMOUNT_MINOR), Currency.getInstance(document.getString(CURRENCY))),
				new DateRange(LocalDate.parse(document.getString(INIT_DATE)), LocalDate.parse(document.getString(END_DATE))));
	}

}

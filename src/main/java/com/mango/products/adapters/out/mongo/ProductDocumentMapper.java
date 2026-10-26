package com.mango.products.adapters.out.mongo;

import java.util.List;

import org.bson.Document;
import org.bson.types.ObjectId;

import com.mango.products.domain.Price;
import com.mango.products.domain.Product;
import com.mango.products.domain.ProductId;

final class ProductDocumentMapper {

	static final String ID = "_id";
	static final String NAME = "name";
	static final String DESCRIPTION = "description";
	static final String PRICES = "prices";
	static final String VERSION = "version";

	private ProductDocumentMapper() {
	}

	static Document toDocument(Product product) {
		return new Document(ID, new ObjectId(product.id().value()))
				.append(NAME, product.name())
				.append(DESCRIPTION, product.description())
				.append(PRICES, product.prices().stream().map(PriceDocumentMapper::toDocument).toList())
				.append(VERSION, product.version());
	}

	static Product toDomain(Document document) {
		List<Price> prices = document.getList(PRICES, Document.class, List.of()).stream()
				.map(PriceDocumentMapper::toDomain)
				.toList();
		return new Product(new ProductId(document.getObjectId(ID).toHexString()), document.getString(NAME),
				document.getString(DESCRIPTION), prices, document.getLong(VERSION));
	}

}

package com.mango.products.adapters.out.mongo;

import java.time.LocalDate;
import java.util.Currency;
import java.util.List;

import com.mango.products.domain.DateRange;
import com.mango.products.domain.Money;
import com.mango.products.domain.Price;
import com.mango.products.domain.Product;
import com.mango.products.domain.ProductId;

final class ProductDocumentMapper {

	private ProductDocumentMapper() {
	}

	static ProductDocument toDocument(Product product) {
		List<PriceDocument> prices = product.prices().stream().map(ProductDocumentMapper::toDocument).toList();
		return new ProductDocument(product.id().value(), product.name(), product.description(), prices,
				product.version());
	}

	static PriceDocument toDocument(Price price) {
		return new PriceDocument(price.money().amountMinor(), price.money().currency().getCurrencyCode(),
				price.period().initDate().toString(), price.period().endDate().toString());
	}

	static Product toDomain(ProductDocument document) {
		List<Price> prices = document.prices() == null ? List.of()
				: document.prices().stream().map(ProductDocumentMapper::toDomain).toList();
		return new Product(new ProductId(document.id()), document.name(), document.description(), prices,
				document.version());
	}

	static Price toDomain(PriceDocument document) {
		return new Price(new Money(document.amountMinor(), Currency.getInstance(document.currency())),
				new DateRange(LocalDate.parse(document.initDate()), LocalDate.parse(document.endDate())));
	}

}

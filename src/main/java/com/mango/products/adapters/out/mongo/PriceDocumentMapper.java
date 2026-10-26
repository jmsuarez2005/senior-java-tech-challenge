package com.mango.products.adapters.out.mongo;

import java.time.LocalDate;
import java.util.Currency;

import org.bson.Document;

import com.mango.products.domain.DateRange;
import com.mango.products.domain.Money;
import com.mango.products.domain.Price;

final class PriceDocumentMapper {

	static final String AMOUNT_MINOR = "amountMinor";
	static final String CURRENCY = "currency";
	static final String INIT_DATE = "initDate";
	static final String END_DATE = "endDate";

	private PriceDocumentMapper() {
	}

	static Document toDocument(Price price) {
		return new Document(AMOUNT_MINOR, price.money().amountMinor())
				.append(CURRENCY, price.money().currency().getCurrencyCode())
				.append(INIT_DATE, price.period().initDate().toString())
				.append(END_DATE, price.period().endDate().toString());
	}

	static Price toDomain(Document document) {
		return new Price(new Money(document.getLong(AMOUNT_MINOR), Currency.getInstance(document.getString(CURRENCY))),
				new DateRange(LocalDate.parse(document.getString(INIT_DATE)), LocalDate.parse(document.getString(END_DATE))));
	}

}

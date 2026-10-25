package com.mango.products.adapters.out.mongo;

// Dates are ISO yyyy-MM-dd strings: they sort like dates and carry no time zone.
record PriceDocument(long amountMinor, String currency, String initDate, String endDate) {
}

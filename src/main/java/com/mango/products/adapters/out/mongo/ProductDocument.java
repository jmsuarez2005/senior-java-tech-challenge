package com.mango.products.adapters.out.mongo;

import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("products")
record ProductDocument(@Id String id, String name, String description, List<PriceDocument> prices, long version) {
}

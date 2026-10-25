package com.mango.products.adapters.out.mongo;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import java.util.Optional;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import com.mango.products.application.port.out.ProductRepository;
import com.mango.products.domain.Price;
import com.mango.products.domain.PriceOverlapException;
import com.mango.products.domain.Product;
import com.mango.products.domain.ProductId;
import com.mango.products.domain.ProductNotFoundException;

@Repository
class MongoProductRepository implements ProductRepository {

	private static final String ID = "_id";
	private static final String PRICES = "prices";
	private static final String VERSION = "version";

	private final MongoTemplate mongo;

	MongoProductRepository(MongoTemplate mongo) {
		this.mongo = mongo;
	}

	@Override
	public ProductId nextId() {
		return new ProductId(ObjectId.get().toHexString());
	}

	@Override
	public Product create(Product product) {
		mongo.insert(ProductDocumentMapper.toDocument(product));
		return product;
	}

	@Override
	public Optional<Product> findById(ProductId id) {
		return Optional.ofNullable(mongo.findById(id.value(), ProductDocument.class)).map(ProductDocumentMapper::toDomain);
	}

	@Override
	public Product addPriceIfNoOverlap(ProductId id, Price price) {
		PriceDocument newPrice = ProductDocumentMapper.toDocument(price);
		Query withoutOverlap = query(where(ID).is(id.value()).and(PRICES).not().elemMatch(overlapping(newPrice)));
		Update push = new Update().push(PRICES, newPrice).inc(VERSION, 1);

		ProductDocument stored = mongo.findAndModify(withoutOverlap, push, FindAndModifyOptions.options().returnNew(true),
				ProductDocument.class);
		if (stored != null) {
			return ProductDocumentMapper.toDomain(stored);
		}
		if (mongo.exists(query(where(ID).is(id.value())), ProductDocument.class)) {
			throw new PriceOverlapException(id, price);
		}
		throw new ProductNotFoundException(id);
	}

	// Closed intervals [a, b] and [c, d] overlap when a <= d and c <= b.
	private static Criteria overlapping(PriceDocument price) {
		return where("currency").is(price.currency())
				.and("initDate").lte(price.endDate())
				.and("endDate").gte(price.initDate());
	}

}

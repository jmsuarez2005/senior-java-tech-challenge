package com.mango.products.adapters.out.mongo;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
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

import reactor.core.publisher.Mono;

@Repository
class MongoProductRepository implements ProductRepository {

	private static final String ID = "_id";
	private static final String PRICES = "prices";
	private static final String VERSION = "version";

	private final ReactiveMongoTemplate mongo;

	MongoProductRepository(ReactiveMongoTemplate mongo) {
		this.mongo = mongo;
	}

	@Override
	public ProductId nextId() {
		return new ProductId(ObjectId.get().toHexString());
	}

	@Override
	public Mono<Product> create(Product product) {
		return mongo.insert(ProductDocumentMapper.toDocument(product)).thenReturn(product);
	}

	@Override
	public Mono<Product> findById(ProductId id) {
		return mongo.findById(id.value(), ProductDocument.class).map(ProductDocumentMapper::toDomain);
	}

	@Override
	public Mono<Product> addPriceIfNoOverlap(ProductId id, Price price) {
		PriceDocument newPrice = ProductDocumentMapper.toDocument(price);
		Query withoutOverlap = query(where(ID).is(id.value()).and(PRICES).not().elemMatch(overlapping(newPrice)));
		Update push = new Update().push(PRICES, newPrice).inc(VERSION, 1);

		return mongo.findAndModify(withoutOverlap, push, FindAndModifyOptions.options().returnNew(true), ProductDocument.class)
				.map(ProductDocumentMapper::toDomain)
				.switchIfEmpty(Mono.defer(() -> explainRejection(id, price)));
	}

	private Mono<Product> explainRejection(ProductId id, Price price) {
		return mongo.exists(byId(id), ProductDocument.class)
				.flatMap(exists -> Mono.error(exists ? new PriceOverlapException(id, price) : new ProductNotFoundException(id)));
	}

	// Closed intervals [a, b] and [c, d] overlap when a <= d and c <= b.
	private static Criteria overlapping(PriceDocument price) {
		return where("currency").is(price.currency())
				.and("initDate").lte(price.endDate())
				.and("endDate").gte(price.initDate());
	}

	private static Query byId(ProductId id) {
		return query(where(ID).is(id.value()));
	}

}

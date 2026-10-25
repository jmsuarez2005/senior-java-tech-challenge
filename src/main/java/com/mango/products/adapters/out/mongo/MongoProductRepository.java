package com.mango.products.adapters.out.mongo;

import static com.mango.products.adapters.out.mongo.ProductDocumentMapper.CURRENCY;
import static com.mango.products.adapters.out.mongo.ProductDocumentMapper.END_DATE;
import static com.mango.products.adapters.out.mongo.ProductDocumentMapper.ID;
import static com.mango.products.adapters.out.mongo.ProductDocumentMapper.INIT_DATE;
import static com.mango.products.adapters.out.mongo.ProductDocumentMapper.PRICES;
import static com.mango.products.adapters.out.mongo.ProductDocumentMapper.VERSION;
import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.elemMatch;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.gte;
import static com.mongodb.client.model.Filters.lte;
import static com.mongodb.client.model.Filters.not;
import static com.mongodb.client.model.Updates.combine;
import static com.mongodb.client.model.Updates.inc;
import static com.mongodb.client.model.Updates.push;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.mango.products.application.port.out.ProductRepository;
import com.mango.products.domain.Price;
import com.mango.products.domain.PriceOverlapException;
import com.mango.products.domain.Product;
import com.mango.products.domain.ProductId;
import com.mango.products.domain.ProductNotFoundException;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;

import io.quarkus.mongodb.reactive.ReactiveMongoClient;
import io.quarkus.mongodb.reactive.ReactiveMongoCollection;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
class MongoProductRepository implements ProductRepository {

	private static final FindOneAndUpdateOptions RETURN_UPDATED = new FindOneAndUpdateOptions()
			.returnDocument(ReturnDocument.AFTER);

	private final ReactiveMongoCollection<Document> products;

	MongoProductRepository(ReactiveMongoClient mongo, @ConfigProperty(name = "quarkus.mongodb.database") String database) {
		this.products = mongo.getDatabase(database).getCollection("products");
	}

	@Override
	public ProductId nextId() {
		return new ProductId(ObjectId.get().toHexString());
	}

	@Override
	public Uni<Product> create(Product product) {
		return products.insertOne(ProductDocumentMapper.toDocument(product)).replaceWith(product);
	}

	@Override
	public Uni<Product> findById(ProductId id) {
		if (!ObjectId.isValid(id.value())) {
			return Uni.createFrom().nullItem();
		}
		return products.find(byId(id)).collect().first()
				.onItem().ifNotNull().transform(ProductDocumentMapper::toDomain);
	}

	@Override
	public Uni<Product> addPriceIfNoOverlap(ProductId id, Price price) {
		if (!ObjectId.isValid(id.value())) {
			return Uni.createFrom().failure(new ProductNotFoundException(id));
		}
		Document newPrice = ProductDocumentMapper.toDocument(price);
		Bson withoutOverlap = and(byId(id), not(elemMatch(PRICES, overlapping(newPrice))));
		Bson addAndBumpVersion = combine(push(PRICES, newPrice), inc(VERSION, 1L));

		return products.findOneAndUpdate(withoutOverlap, addAndBumpVersion, RETURN_UPDATED)
				.onItem().ifNotNull().transform(ProductDocumentMapper::toDomain)
				.onItem().ifNull().switchTo(() -> explainRejection(id, price));
	}

	private Uni<Product> explainRejection(ProductId id, Price price) {
		return products.countDocuments(byId(id))
				.chain(count -> Uni.createFrom().<Product>failure(
						count > 0 ? new PriceOverlapException(id, price) : new ProductNotFoundException(id)));
	}

	// Closed intervals [a, b] and [c, d] overlap when a <= d and c <= b.
	private static Bson overlapping(Document price) {
		return and(eq(CURRENCY, price.getString(CURRENCY)),
				lte(INIT_DATE, price.getString(END_DATE)),
				gte(END_DATE, price.getString(INIT_DATE)));
	}

	private static Bson byId(ProductId id) {
		return eq(ID, new ObjectId(id.value()));
	}

}

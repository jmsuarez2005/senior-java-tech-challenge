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

import java.util.Optional;

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
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
class MongoProductRepository implements ProductRepository {

	private static final FindOneAndUpdateOptions RETURN_UPDATED = new FindOneAndUpdateOptions()
			.returnDocument(ReturnDocument.AFTER);

	private final MongoCollection<Document> products;

	MongoProductRepository(MongoClient mongo, @ConfigProperty(name = "quarkus.mongodb.database") String database) {
		this.products = mongo.getDatabase(database).getCollection("products");
	}

	@Override
	public ProductId nextId() {
		return new ProductId(ObjectId.get().toHexString());
	}

	@Override
	public Product create(Product product) {
		products.insertOne(ProductDocumentMapper.toDocument(product));
		return product;
	}

	@Override
	public Optional<Product> findById(ProductId id) {
		if (!ObjectId.isValid(id.value())) {
			return Optional.empty();
		}
		return Optional.ofNullable(products.find(byId(id)).first()).map(ProductDocumentMapper::toDomain);
	}

	@Override
	public Product addPriceIfNoOverlap(ProductId id, Price price) {
		if (!ObjectId.isValid(id.value())) {
			throw new ProductNotFoundException(id);
		}
		Document newPrice = ProductDocumentMapper.toDocument(price);
		Bson withoutOverlap = and(byId(id), not(elemMatch(PRICES, overlapping(newPrice))));
		Bson addAndBumpVersion = combine(push(PRICES, newPrice), inc(VERSION, 1L));

		Document stored = products.findOneAndUpdate(withoutOverlap, addAndBumpVersion, RETURN_UPDATED);
		if (stored != null) {
			return ProductDocumentMapper.toDomain(stored);
		}
		if (products.countDocuments(byId(id)) > 0) {
			throw new PriceOverlapException(id, price);
		}
		throw new ProductNotFoundException(id);
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

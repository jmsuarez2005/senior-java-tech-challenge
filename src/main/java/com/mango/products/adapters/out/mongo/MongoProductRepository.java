package com.mango.products.adapters.out.mongo;

import static com.mango.products.adapters.out.mongo.ProductDocumentMapper.PRICES;
import static com.mango.products.adapters.out.mongo.ProductDocumentMapper.VERSION;
import static com.mango.products.adapters.out.mongo.ProductFilters.arrayElement;
import static com.mango.products.adapters.out.mongo.ProductFilters.byId;
import static com.mango.products.adapters.out.mongo.ProductFilters.hasPrice;
import static com.mango.products.adapters.out.mongo.ProductFilters.noOtherPriceOverlapping;
import static com.mango.products.adapters.out.mongo.ProductFilters.noPriceOverlapping;
import static com.mango.products.adapters.out.mongo.ProductFilters.samePrice;
import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Updates.combine;
import static com.mongodb.client.model.Updates.inc;
import static com.mongodb.client.model.Updates.pull;
import static com.mongodb.client.model.Updates.push;
import static com.mongodb.client.model.Updates.set;

import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.OptionalLong;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.mango.products.application.port.out.PriceWriter;
import com.mango.products.application.port.out.ProductReader;
import com.mango.products.application.port.out.ProductWriter;
import com.mango.products.domain.Price;
import com.mango.products.domain.PriceNotFoundException;
import com.mango.products.domain.PriceOverlapException;
import com.mango.products.domain.Product;
import com.mango.products.domain.ProductChangedException;
import com.mango.products.domain.ProductId;
import com.mango.products.domain.ProductNotFoundException;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;

import io.quarkus.mongodb.reactive.ReactiveMongoClient;
import io.quarkus.mongodb.reactive.ReactiveMongoCollection;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
class MongoProductRepository implements ProductReader, ProductWriter, PriceWriter {

	private static final FindOneAndUpdateOptions RETURN_UPDATED = new FindOneAndUpdateOptions()
			.returnDocument(ReturnDocument.AFTER);
	private static final String MATCHED_PRICE = "p";

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
		Document newPrice = PriceDocumentMapper.toDocument(price);
		return products.findOneAndUpdate(and(byId(id), noPriceOverlapping(newPrice)),
				combine(push(PRICES, newPrice), inc(VERSION, 1L)), RETURN_UPDATED)
				.onItem().ifNotNull().transform(ProductDocumentMapper::toDomain)
				.onItem().ifNull().switchTo(() -> explainRejectedAddition(id, price));
	}

	@Override
	public Uni<Product> replacePriceIfNoOverlap(ProductId id, Price price, OptionalLong expectedVersion) {
		if (!ObjectId.isValid(id.value())) {
			return Uni.createFrom().failure(new ProductNotFoundException(id));
		}
		Currency currency = price.money().currency();
		LocalDate initDate = price.period().initDate();
		Document newPrice = PriceDocumentMapper.toDocument(price);
		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions()
				.returnDocument(ReturnDocument.AFTER)
				.arrayFilters(List.of(arrayElement(MATCHED_PRICE, newPrice)));

		return products.findOneAndUpdate(
				and(byId(id, expectedVersion), hasPrice(currency, initDate), noOtherPriceOverlapping(newPrice)),
				combine(set(PRICES + ".$[" + MATCHED_PRICE + "]", newPrice), inc(VERSION, 1L)), options)
				.onItem().ifNotNull().transform(ProductDocumentMapper::toDomain)
				.onItem().ifNull().switchTo(() -> explainRejectedReplacement(id, price, expectedVersion));
	}

	@Override
	public Uni<Product> removePrice(ProductId id, Currency currency, LocalDate initDate, OptionalLong expectedVersion) {
		if (!ObjectId.isValid(id.value())) {
			return Uni.createFrom().failure(new ProductNotFoundException(id));
		}
		return products.findOneAndUpdate(and(byId(id, expectedVersion), hasPrice(currency, initDate)),
				combine(pull(PRICES, samePrice(currency, initDate)), inc(VERSION, 1L)), RETURN_UPDATED)
				.onItem().ifNotNull().transform(ProductDocumentMapper::toDomain)
				.onItem().ifNull().switchTo(() -> explainRejectedRemoval(id, currency, initDate, expectedVersion));
	}

	private Uni<Product> explainRejectedAddition(ProductId id, Price price) {
		return products.countDocuments(byId(id))
				.chain(count -> Uni.createFrom().<Product>failure(
						count > 0 ? new PriceOverlapException(id, price) : new ProductNotFoundException(id)));
	}

	private Uni<Product> explainRejectedReplacement(ProductId id, Price price, OptionalLong expectedVersion) {
		Currency currency = price.money().currency();
		LocalDate initDate = price.period().initDate();
		return unchangedProduct(id, expectedVersion)
				.chain(product -> Uni.createFrom().failure(product.hasPriceStartingOn(initDate, currency)
						? new PriceOverlapException(id, price)
						: PriceNotFoundException.startingOn(id, initDate, currency)));
	}

	private Uni<Product> explainRejectedRemoval(ProductId id, Currency currency, LocalDate initDate,
			OptionalLong expectedVersion) {
		return unchangedProduct(id, expectedVersion)
				.chain(product -> Uni.createFrom().failure(PriceNotFoundException.startingOn(id, initDate, currency)));
	}

	private Uni<Product> unchangedProduct(ProductId id, OptionalLong expectedVersion) {
		return findById(id)
				.onItem().ifNull().failWith(() -> new ProductNotFoundException(id))
				.invoke(product -> {
					if (expectedVersion.isPresent() && product.version() != expectedVersion.getAsLong()) {
						throw new ProductChangedException(id, expectedVersion.getAsLong());
					}
				});
	}

}

package com.mango.products.adapters.out.mongo;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;

import com.mango.products.MongoTestcontainersConfiguration;
import com.mango.products.domain.DateRange;
import com.mango.products.domain.Money;
import com.mango.products.domain.Price;
import com.mango.products.domain.PriceOverlapException;
import com.mango.products.domain.Product;
import com.mango.products.domain.ProductId;
import com.mango.products.domain.ProductNotFoundException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DataMongoTest
@Import({ MongoTestcontainersConfiguration.class, MongoProductRepository.class })
class MongoProductRepositoryIT {

	private static final Currency EUR = Currency.getInstance("EUR");
	private static final Currency USD = Currency.getInstance("USD");

	@Autowired
	MongoProductRepository repository;

	@Test
	void createdProductCanBeFoundById() {
		Product product = newProduct();

		StepVerifier.create(repository.create(product).then(repository.findById(product.id())))
				.expectNext(product)
				.verifyComplete();
	}

	@Test
	void findByIdOfUnknownProductIsEmpty() {
		StepVerifier.create(repository.findById(repository.nextId())).verifyComplete();
	}

	@Test
	void addedPricesComeBackOrderedByInitDate() {
		ProductId id = createProduct();
		Price later = price("199.99", EUR, "2025-01-01", "2025-06-30");
		Price earlier = price("99.99", EUR, "2024-01-01", "2024-06-30");

		Mono<Product> stored = repository.addPriceIfNoOverlap(id, later)
				.then(repository.addPriceIfNoOverlap(id, earlier))
				.then(repository.findById(id));

		StepVerifier.create(stored)
				.assertNext(product -> assertThat(product.prices()).containsExactly(earlier, later))
				.verifyComplete();
	}

	@Test
	void addingAPriceReturnsTheStoredProductWithTheNextVersion() {
		ProductId id = createProduct();
		Price price = price("99.99", EUR, "2024-01-01", null);

		StepVerifier.create(repository.addPriceIfNoOverlap(id, price))
				.assertNext(product -> {
					assertThat(product.prices()).containsExactly(price);
					assertThat(product.version()).isEqualTo(1);
				})
				.verifyComplete();
	}

	@Test
	void rejectsPriceOverlappingByASingleDay() {
		ProductId id = createProduct();
		repository.addPriceIfNoOverlap(id, price("99.99", EUR, "2024-01-01", "2024-06-30")).block();

		StepVerifier.create(repository.addPriceIfNoOverlap(id, price("89.99", EUR, "2024-06-30", "2024-12-31")))
				.expectError(PriceOverlapException.class)
				.verify();
	}

	@Test
	void acceptsAdjacentPriceAndSamePeriodInAnotherCurrency() {
		ProductId id = createProduct();
		repository.addPriceIfNoOverlap(id, price("99.99", EUR, "2024-01-01", "2024-06-30")).block();

		StepVerifier.create(repository.addPriceIfNoOverlap(id, price("89.99", EUR, "2024-07-01", null))
				.then(repository.addPriceIfNoOverlap(id, price("109.99", USD, "2024-01-01", "2024-06-30"))))
				.expectNextCount(1)
				.verifyComplete();
	}

	@Test
	void addingPriceToUnknownProductFails() {
		StepVerifier.create(repository.addPriceIfNoOverlap(repository.nextId(), price("99.99", EUR, "2024-01-01", null)))
				.expectError(ProductNotFoundException.class)
				.verify();
	}

	@Test
	void concurrentOverlappingInsertsLetExactlyOneThrough() {
		ProductId id = createProduct();
		int attempts = 50;

		List<Mono<Boolean>> inserts = Flux.range(1, attempts)
				.map(i -> repository.addPriceIfNoOverlap(id, price(i + ".00", EUR, "2024-01-01", "2024-12-31"))
						.thenReturn(true)
						.onErrorResume(PriceOverlapException.class, e -> Mono.just(false)))
				.collectList()
				.block();

		StepVerifier.create(Flux.merge(inserts).collectList())
				.assertNext(results -> {
					assertThat(results).hasSize(attempts);
					assertThat(results).filteredOn(Boolean::booleanValue).hasSize(1);
				})
				.verifyComplete();
		StepVerifier.create(repository.findById(id))
				.assertNext(product -> assertThat(product.prices()).hasSize(1))
				.verifyComplete();
	}

	private ProductId createProduct() {
		return repository.create(newProduct()).map(Product::id).block();
	}

	private Product newProduct() {
		return Product.create(repository.nextId(), "Sneakers", "2025 limited edition");
	}

	private static Price price(String value, Currency currency, String initDate, String endDate) {
		return new Price(Money.of(new BigDecimal(value), currency),
				new DateRange(LocalDate.parse(initDate), endDate == null ? null : LocalDate.parse(endDate)));
	}

}

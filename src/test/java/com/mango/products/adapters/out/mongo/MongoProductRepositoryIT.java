package com.mango.products.adapters.out.mongo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

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

@DataMongoTest
@Import({ MongoTestcontainersConfiguration.class, MongoProductRepository.class })
class MongoProductRepositoryIT {

	private static final Currency EUR = Currency.getInstance("EUR");
	private static final Currency USD = Currency.getInstance("USD");

	@Autowired
	MongoProductRepository repository;

	@Test
	void createdProductCanBeFoundById() {
		Product product = repository.create(newProduct());

		assertThat(repository.findById(product.id())).contains(product);
	}

	@Test
	void findByIdOfUnknownProductIsEmpty() {
		assertThat(repository.findById(repository.nextId())).isEmpty();
	}

	@Test
	void addedPricesComeBackOrderedByInitDate() {
		ProductId id = createProduct();
		Price later = price("199.99", EUR, "2025-01-01", "2025-06-30");
		Price earlier = price("99.99", EUR, "2024-01-01", "2024-06-30");

		repository.addPriceIfNoOverlap(id, later);
		repository.addPriceIfNoOverlap(id, earlier);

		assertThat(repository.findById(id)).hasValueSatisfying(
				product -> assertThat(product.prices()).containsExactly(earlier, later));
	}

	@Test
	void addingAPriceReturnsTheStoredProductWithTheNextVersion() {
		ProductId id = createProduct();
		Price price = price("99.99", EUR, "2024-01-01", null);

		Product stored = repository.addPriceIfNoOverlap(id, price);

		assertThat(stored.prices()).containsExactly(price);
		assertThat(stored.version()).isEqualTo(1);
	}

	@Test
	void rejectsPriceOverlappingByASingleDay() {
		ProductId id = createProduct();
		repository.addPriceIfNoOverlap(id, price("99.99", EUR, "2024-01-01", "2024-06-30"));

		assertThatThrownBy(() -> repository.addPriceIfNoOverlap(id, price("89.99", EUR, "2024-06-30", "2024-12-31")))
				.isInstanceOf(PriceOverlapException.class);
	}

	@Test
	void acceptsAdjacentPriceAndSamePeriodInAnotherCurrency() {
		ProductId id = createProduct();
		repository.addPriceIfNoOverlap(id, price("99.99", EUR, "2024-01-01", "2024-06-30"));

		repository.addPriceIfNoOverlap(id, price("89.99", EUR, "2024-07-01", null));
		Product stored = repository.addPriceIfNoOverlap(id, price("109.99", USD, "2024-01-01", "2024-06-30"));

		assertThat(stored.prices()).hasSize(3);
	}

	@Test
	void addingPriceToUnknownProductFails() {
		assertThatThrownBy(() -> repository.addPriceIfNoOverlap(repository.nextId(), price("99.99", EUR, "2024-01-01", null)))
				.isInstanceOf(ProductNotFoundException.class);
	}

	@Test
	void concurrentOverlappingInsertsLetExactlyOneThrough() throws InterruptedException {
		ProductId id = createProduct();
		int attempts = 50;
		List<Callable<Boolean>> inserts = IntStream.rangeClosed(1, attempts)
				.<Callable<Boolean>>mapToObj(i -> () -> tryInsert(id, price(i + ".00", EUR, "2024-01-01", "2024-12-31")))
				.toList();

		List<Boolean> results;
		try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
			results = executor.invokeAll(inserts).stream().map(MongoProductRepositoryIT::resultOf).toList();
		}

		assertThat(results).hasSize(attempts).filteredOn(Boolean::booleanValue).hasSize(1);
		assertThat(repository.findById(id)).hasValueSatisfying(product -> assertThat(product.prices()).hasSize(1));
	}

	private boolean tryInsert(ProductId id, Price price) {
		try {
			repository.addPriceIfNoOverlap(id, price);
			return true;
		}
		catch (PriceOverlapException e) {
			return false;
		}
	}

	private static Boolean resultOf(Future<Boolean> future) {
		try {
			return future.get();
		}
		catch (InterruptedException | ExecutionException e) {
			throw new IllegalStateException(e);
		}
	}

	private ProductId createProduct() {
		return repository.create(newProduct()).id();
	}

	private Product newProduct() {
		return Product.create(repository.nextId(), "Sneakers", "2025 limited edition");
	}

	private static Price price(String value, Currency currency, String initDate, String endDate) {
		return new Price(Money.of(new BigDecimal(value), currency),
				new DateRange(LocalDate.parse(initDate), endDate == null ? null : LocalDate.parse(endDate)));
	}

}

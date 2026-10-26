package com.mango.products.adapters.out.mongo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.mango.products.domain.DateRange;
import com.mango.products.domain.Money;
import com.mango.products.domain.Price;
import com.mango.products.domain.PriceNotFoundException;
import com.mango.products.domain.PriceOverlapException;
import com.mango.products.domain.Product;
import com.mango.products.domain.ProductId;
import com.mango.products.domain.ProductNotFoundException;

import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@QuarkusTest
class MongoProductRepositoryIT {

	private static final Currency EUR = Currency.getInstance("EUR");
	private static final Currency USD = Currency.getInstance("USD");
	private static final Duration TIMEOUT = Duration.ofSeconds(10);

	@Inject
	MongoProductRepository repository;

	@Test
	void createdProductCanBeFoundById() {
		Product product = await(repository.create(newProduct()));

		assertThat(await(repository.findById(product.id()))).isEqualTo(product);
	}

	@Test
	void findByIdOfUnknownProductIsNull() {
		assertThat(await(repository.findById(repository.nextId()))).isNull();
	}

	@Test
	void addedPricesComeBackOrderedByInitDate() {
		ProductId id = createProduct();
		Price later = price("199.99", EUR, "2025-01-01", "2025-06-30");
		Price earlier = price("99.99", EUR, "2024-01-01", "2024-06-30");

		await(repository.addPriceIfNoOverlap(id, later));
		await(repository.addPriceIfNoOverlap(id, earlier));

		assertThat(await(repository.findById(id)).prices()).containsExactly(earlier, later);
	}

	@Test
	void addingAPriceReturnsTheStoredProductWithTheNextVersion() {
		ProductId id = createProduct();
		Price price = price("99.99", EUR, "2024-01-01", null);

		Product stored = await(repository.addPriceIfNoOverlap(id, price));

		assertThat(stored.prices()).containsExactly(price);
		assertThat(stored.version()).isEqualTo(1);
	}

	@Test
	void rejectsPriceOverlappingByASingleDay() {
		ProductId id = createProduct();
		await(repository.addPriceIfNoOverlap(id, price("99.99", EUR, "2024-01-01", "2024-06-30")));

		assertThatThrownBy(() -> await(repository.addPriceIfNoOverlap(id, price("89.99", EUR, "2024-06-30", "2024-12-31"))))
				.isInstanceOf(PriceOverlapException.class);
	}

	@Test
	void acceptsAdjacentPriceAndSamePeriodInAnotherCurrency() {
		ProductId id = createProduct();
		await(repository.addPriceIfNoOverlap(id, price("99.99", EUR, "2024-01-01", "2024-06-30")));

		await(repository.addPriceIfNoOverlap(id, price("89.99", EUR, "2024-07-01", null)));
		Product stored = await(repository.addPriceIfNoOverlap(id, price("109.99", USD, "2024-01-01", "2024-06-30")));

		assertThat(stored.prices()).hasSize(3);
	}

	@Test
	void addingPriceToUnknownProductFails() {
		assertThatThrownBy(() -> await(repository.addPriceIfNoOverlap(repository.nextId(), price("99.99", EUR, "2024-01-01", null))))
				.isInstanceOf(ProductNotFoundException.class);
	}

	@Test
	void replacesAPriceKeepingItsIdentity() {
		ProductId id = createProduct();
		await(repository.addPriceIfNoOverlap(id, price("99.99", EUR, "2024-01-01", "2024-06-30")));
		Price shorter = price("89.99", EUR, "2024-01-01", "2024-03-31");

		Product stored = await(repository.replacePriceIfNoOverlap(id, shorter));

		assertThat(stored.prices()).containsExactly(shorter);
		assertThat(stored.version()).isEqualTo(2);
	}

	@Test
	void replacementMayNotOverlapAnotherPriceButMayCoverItsOwnOldPeriod() {
		ProductId id = createProduct();
		await(repository.addPriceIfNoOverlap(id, price("99.99", EUR, "2024-01-01", "2024-06-30")));
		await(repository.addPriceIfNoOverlap(id, price("199.99", EUR, "2025-01-01", null)));

		assertThat(await(repository.replacePriceIfNoOverlap(id, price("99.99", EUR, "2024-01-01", "2024-12-31"))).prices())
				.hasSize(2);
		assertThatThrownBy(() -> await(repository.replacePriceIfNoOverlap(id, price("99.99", EUR, "2024-01-01", "2025-01-01"))))
				.isInstanceOf(PriceOverlapException.class);
	}

	@Test
	void replacingAMissingPriceFails() {
		ProductId id = createProduct();

		assertThatThrownBy(() -> await(repository.replacePriceIfNoOverlap(id, price("9.99", EUR, "2024-01-01", null))))
				.isInstanceOf(PriceNotFoundException.class);
		assertThatThrownBy(() -> await(repository.replacePriceIfNoOverlap(repository.nextId(), price("9.99", EUR, "2024-01-01", null))))
				.isInstanceOf(ProductNotFoundException.class);
	}

	@Test
	void removesAPriceOnlyOnce() {
		ProductId id = createProduct();
		await(repository.addPriceIfNoOverlap(id, price("99.99", EUR, "2024-01-01", null)));

		assertThat(await(repository.removePrice(id, EUR, LocalDate.parse("2024-01-01"))).prices()).isEmpty();
		assertThatThrownBy(() -> await(repository.removePrice(id, EUR, LocalDate.parse("2024-01-01"))))
				.isInstanceOf(PriceNotFoundException.class);
		assertThatThrownBy(() -> await(repository.removePrice(repository.nextId(), EUR, LocalDate.parse("2024-01-01"))))
				.isInstanceOf(ProductNotFoundException.class);
	}

	@Test
	void concurrentOverlappingInsertsLetExactlyOneThrough() {
		ProductId id = createProduct();
		int attempts = 50;
		List<Uni<Boolean>> inserts = IntStream.rangeClosed(1, attempts)
				.mapToObj(i -> repository.addPriceIfNoOverlap(id, price(i + ".00", EUR, "2024-01-01", "2024-12-31"))
						.replaceWith(true)
						.onFailure(PriceOverlapException.class).recoverWithItem(false))
				.toList();

		List<Boolean> results = await(Uni.join().all(inserts).andFailFast());

		assertThat(results).hasSize(attempts).filteredOn(Boolean::booleanValue).hasSize(1);
		assertThat(await(repository.findById(id)).prices()).hasSize(1);
	}

	private ProductId createProduct() {
		return await(repository.create(newProduct())).id();
	}

	private Product newProduct() {
		return Product.create(repository.nextId(), "Sneakers", "2025 limited edition");
	}

	private static <T> T await(Uni<T> uni) {
		return uni.await().atMost(TIMEOUT);
	}

	private static Price price(String value, Currency currency, String initDate, String endDate) {
		return new Price(Money.of(new BigDecimal(value), currency),
				new DateRange(LocalDate.parse(initDate), endDate == null ? null : LocalDate.parse(endDate)));
	}

}

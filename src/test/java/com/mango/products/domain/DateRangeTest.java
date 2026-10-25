package com.mango.products.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DateRangeTest {

	private static final DateRange FIRST_HALF_2024 = range("2024-01-01", "2024-06-30");

	@ParameterizedTest(name = "{0}: [{1}, {2}] overlaps={3}")
	@CsvSource(nullValues = "null", textBlock = """
			starts inside,          2024-03-01, 2024-12-31, true
			ends inside,            2023-10-01, 2024-03-01, true
			contains,               2023-01-01, 2025-01-01, true
			contained,              2024-02-01, 2024-03-01, true
			adjacent by one day,    2024-06-30, 2024-12-31, true
			adjacent before,        2023-06-01, 2024-01-01, true
			separated after,        2024-07-01, 2024-12-31, false
			separated before,       2023-01-01, 2023-12-31, false
			open end after,         2024-07-01, null,       false
			open end inside,        2024-06-01, null,       true
			""")
	void overlapsIsClosedOnBothEndsAndSymmetric(String scenario, String init, String end, boolean expected) {
		DateRange other = range(init, end);

		assertThat(FIRST_HALF_2024.overlaps(other)).isEqualTo(expected);
		assertThat(other.overlaps(FIRST_HALF_2024)).isEqualTo(expected);
	}

	@Test
	void twoOpenEndedRangesAlwaysOverlap() {
		assertThat(range("2024-01-01", null).overlaps(range("2030-01-01", null))).isTrue();
	}

	@Test
	void nullEndDateMeansOpenEnded() {
		DateRange open = range("2024-01-01", null);

		assertThat(open.isOpenEnded()).isTrue();
		assertThat(open.endDate()).isEqualTo(DateRange.OPEN_END);
		assertThat(open.contains(LocalDate.of(9999, 12, 31))).isTrue();
	}

	@ParameterizedTest
	@CsvSource({ "2024-01-01, true", "2024-04-15, true", "2024-06-30, true", "2023-12-31, false", "2024-07-01, false" })
	void containsIncludesBothBoundaries(String date, boolean expected) {
		assertThat(FIRST_HALF_2024.contains(LocalDate.parse(date))).isEqualTo(expected);
	}

	@ParameterizedTest(name = "[{0}, {1}] is rejected on {2}")
	@CsvSource(nullValues = "null", textBlock = """
			null,       2024-06-30, initDate
			2024-06-30, 2024-06-30, endDate
			2024-07-01, 2024-06-30, endDate
			""")
	void rejectsInvalidRanges(String init, String end, String field) {
		assertThatThrownBy(() -> range(init, end))
				.isInstanceOfSatisfying(DomainValidationException.class, e -> assertThat(e.field()).isEqualTo(field));
	}

	@Test
	void rejectsEndDateBeyondSentinel() {
		assertThatThrownBy(() -> new DateRange(LocalDate.of(2024, 1, 1), DateRange.OPEN_END.plusDays(1)))
				.isInstanceOf(DomainValidationException.class);
	}

	private static DateRange range(String init, String end) {
		return new DateRange(init == null ? null : LocalDate.parse(init), end == null ? null : LocalDate.parse(end));
	}

}

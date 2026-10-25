package com.mango.products.domain;

import java.time.LocalDate;

// Closed interval, both days included. A missing end date becomes OPEN_END.
public record DateRange(LocalDate initDate, LocalDate endDate) {

	public static final LocalDate OPEN_END = LocalDate.of(9999, 12, 31);

	public DateRange {
		if (initDate == null) {
			throw new DomainValidationException("initDate", "initDate is required");
		}
		if (!isValid(initDate, endDate)) {
			throw new DomainValidationException("endDate", "endDate must be after initDate and not after " + OPEN_END);
		}
		if (endDate == null) {
			endDate = OPEN_END;
		}
	}

	public static boolean isValid(LocalDate initDate, LocalDate endDate) {
		LocalDate end = endDate == null ? OPEN_END : endDate;
		return initDate.isBefore(end) && !end.isAfter(OPEN_END);
	}

	public boolean isOpenEnded() {
		return endDate.equals(OPEN_END);
	}

	public boolean overlaps(DateRange other) {
		return !initDate.isAfter(other.endDate) && !other.initDate.isAfter(endDate);
	}

	public boolean contains(LocalDate date) {
		return !date.isBefore(initDate) && !date.isAfter(endDate);
	}

	@Override
	public String toString() {
		return "[" + initDate + ", " + (isOpenEnded() ? "open" : endDate) + "]";
	}

}

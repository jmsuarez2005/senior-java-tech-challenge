package com.mango.products.adapters.in.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// Currency and initDate identify the price and come from the path; only these two can change.
public record ReplacePriceRequest(
		@Schema(examples = "89.99") @NotNull @Positive BigDecimal value,
		@Schema(examples = "2024-03-31", description = "Inclusive. Null means until further notice.") LocalDate endDate) {
}

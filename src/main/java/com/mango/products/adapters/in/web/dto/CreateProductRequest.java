package com.mango.products.adapters.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateProductRequest(
		@Schema(example = "Zapatillas deportivas") @NotBlank @Size(max = 255) String name,
		@Schema(example = "Modelo 2025 edición limitada") @Size(max = 2000) String description) {
}

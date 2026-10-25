package com.mango.products.adapters.in.web.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateProductRequest(
		@Schema(examples = "Zapatillas deportivas") @NotBlank @Size(max = 255) String name,
		@Schema(examples = "Modelo 2025 edición limitada") @Size(max = 2000) String description) {
}

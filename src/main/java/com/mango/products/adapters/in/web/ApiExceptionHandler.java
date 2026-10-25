package com.mango.products.adapters.in.web;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.reactive.result.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.server.ServerWebExchange;

import com.mango.products.domain.DomainException;
import com.mango.products.domain.DomainValidationException;
import com.mango.products.domain.PriceNotFoundException;
import com.mango.products.domain.PriceOverlapException;
import com.mango.products.domain.ProductNotFoundException;

import reactor.core.publisher.Mono;

@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	private static final String VALIDATION_FAILED = "Request validation failed";

	record FieldViolation(String field, String message) {
	}

	@ExceptionHandler(DomainException.class)
	ProblemDetail handleDomainException(DomainException exception) {
		return switch (exception) {
			case DomainValidationException e -> validationProblem(List.of(new FieldViolation(e.field(), e.getMessage())));
			case ProductNotFoundException e -> ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
			case PriceNotFoundException e -> ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
			case PriceOverlapException e -> ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
		};
	}

	@Override
	protected Mono<ResponseEntity<Object>> handleWebExchangeBindException(WebExchangeBindException exception,
			HttpHeaders headers, HttpStatusCode status, ServerWebExchange exchange) {
		List<FieldViolation> violations = exception.getFieldErrors().stream()
				.map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
				.toList();
		return handleExceptionInternal(exception, validationProblem(violations), headers, status, exchange);
	}

	private static ProblemDetail validationProblem(List<FieldViolation> violations) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, VALIDATION_FAILED);
		problem.setProperty("errors", violations);
		return problem;
	}

}

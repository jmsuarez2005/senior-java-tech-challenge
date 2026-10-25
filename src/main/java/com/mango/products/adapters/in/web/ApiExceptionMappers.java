package com.mango.products.adapters.in.web;

import java.util.List;

import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.mango.products.adapters.in.web.Problem.FieldViolation;
import com.mango.products.domain.DomainException;
import com.mango.products.domain.DomainValidationException;
import com.mango.products.domain.PriceNotFoundException;
import com.mango.products.domain.PriceOverlapException;
import com.mango.products.domain.ProductNotFoundException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.UriInfo;

public class ApiExceptionMappers {

	private static final String VALIDATION_FAILED = "Request validation failed";

	@ServerExceptionMapper
	public Response domain(DomainException exception, UriInfo uri) {
		String path = uri.getPath();
		return switch (exception) {
			case DomainValidationException e -> Problem.response(Status.BAD_REQUEST, VALIDATION_FAILED, path,
					List.of(new FieldViolation(e.field(), e.getMessage())));
			case ProductNotFoundException e -> Problem.response(Status.NOT_FOUND, e.getMessage(), path);
			case PriceNotFoundException e -> Problem.response(Status.NOT_FOUND, e.getMessage(), path);
			case PriceOverlapException e -> Problem.response(Status.CONFLICT, e.getMessage(), path);
		};
	}

	@ServerExceptionMapper
	public Response invalidRequest(ConstraintViolationException exception, UriInfo uri) {
		List<FieldViolation> violations = exception.getConstraintViolations().stream()
				.map(violation -> new FieldViolation(fieldName(violation), violation.getMessage()))
				.toList();
		return Problem.response(Status.BAD_REQUEST, VALIDATION_FAILED, uri.getPath(), violations);
	}

	@ServerExceptionMapper
	public Response unreadableBody(JsonProcessingException exception, UriInfo uri) {
		return Problem.response(Status.BAD_REQUEST, "Malformed request body", uri.getPath());
	}

	// Violations come as "method.argument.field"; clients only care about the field.
	private static String fieldName(ConstraintViolation<?> violation) {
		String field = null;
		for (Path.Node node : violation.getPropertyPath()) {
			field = node.getName();
		}
		return field;
	}

}

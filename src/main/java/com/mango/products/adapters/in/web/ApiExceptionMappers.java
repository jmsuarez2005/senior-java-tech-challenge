package com.mango.products.adapters.in.web;

import java.util.List;

import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException.Reference;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.mango.products.adapters.in.web.Problem.FieldViolation;
import com.mango.products.domain.DomainException;
import com.mango.products.domain.DomainValidationException;
import com.mango.products.domain.PriceNotFoundException;
import com.mango.products.domain.PriceOverlapException;
import com.mango.products.domain.ProductChangedException;
import com.mango.products.domain.ProductNotFoundException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.UriInfo;

public class ApiExceptionMappers {

	private static final String VALIDATION_FAILED = "Request validation failed";
	private static final String MALFORMED_BODY = "Malformed request body";

	@ServerExceptionMapper
	public Response domain(DomainException exception, UriInfo uri) {
		String path = uri.getPath();
		return switch (exception) {
			case DomainValidationException e -> Problem.response(Status.BAD_REQUEST, VALIDATION_FAILED, path,
					List.of(new FieldViolation(e.field(), e.getMessage())));
			case ProductNotFoundException e -> Problem.response(Status.NOT_FOUND, e.getMessage(), path);
			case PriceNotFoundException e -> Problem.response(Status.NOT_FOUND, e.getMessage(), path);
			case PriceOverlapException e -> Problem.response(Status.CONFLICT, e.getMessage(), path);
			case ProductChangedException e -> Problem.response(Status.PRECONDITION_FAILED, e.getMessage(), path);
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
	public Response invalidField(MismatchedInputException exception, UriInfo uri) {
		String field = exception.getPath().stream()
				.map(Reference::getFieldName)
				.filter(name -> name != null)
				.reduce((first, second) -> second)
				.orElse(null);
		if (field == null) {
			return Problem.response(Status.BAD_REQUEST, MALFORMED_BODY, uri.getPath());
		}
		return Problem.response(Status.BAD_REQUEST, VALIDATION_FAILED, uri.getPath(),
				List.of(new FieldViolation(field, "has an invalid value or format")));
	}

	@ServerExceptionMapper
	public Response unreadableBody(JsonProcessingException exception, UriInfo uri) {
		return Problem.response(Status.BAD_REQUEST, MALFORMED_BODY, uri.getPath());
	}

	@ServerExceptionMapper
	public Response clientError(WebApplicationException exception, UriInfo uri) {
		Response response = exception.getResponse();
		if (response.getStatus() >= 500) {
			return response;
		}
		Status status = Status.fromStatusCode(response.getStatus());
		String detail = status == Status.BAD_REQUEST ? MALFORMED_BODY : status.getReasonPhrase();
		return Problem.response(status, detail, uri.getPath());
	}

	private static String fieldName(ConstraintViolation<?> violation) {
		String field = null;
		for (Path.Node node : violation.getPropertyPath()) {
			field = node.getName();
		}
		return field;
	}

}

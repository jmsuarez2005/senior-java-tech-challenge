package com.mango.products.adapters.in.web;

import java.util.List;

import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import com.mango.products.adapters.in.web.Problem.FieldViolation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.UriInfo;

public class ValidationExceptionMapper {

	@ServerExceptionMapper
	public Response toProblem(ConstraintViolationException exception, UriInfo uri) {
		List<FieldViolation> violations = exception.getConstraintViolations().stream()
				.map(violation -> new FieldViolation(fieldName(violation), violation.getMessage()))
				.toList();
		return Problem.response(Status.BAD_REQUEST, Problem.VALIDATION_FAILED, uri.getPath(), violations);
	}

	private static String fieldName(ConstraintViolation<?> violation) {
		String field = null;
		for (Path.Node node : violation.getPropertyPath()) {
			field = node.getName();
		}
		return field;
	}

}

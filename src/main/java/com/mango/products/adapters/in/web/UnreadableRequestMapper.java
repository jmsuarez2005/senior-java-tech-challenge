package com.mango.products.adapters.in.web;

import java.util.List;
import java.util.Objects;

import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException.Reference;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.mango.products.adapters.in.web.Problem.FieldViolation;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.UriInfo;

public class UnreadableRequestMapper {

	@ServerExceptionMapper
	public Response invalidField(MismatchedInputException exception, UriInfo uri) {
		return exception.getPath().stream()
				.map(Reference::getFieldName)
				.filter(Objects::nonNull)
				.reduce((first, second) -> second)
				.map(field -> Problem.response(Status.BAD_REQUEST, Problem.VALIDATION_FAILED, uri.getPath(),
						List.of(new FieldViolation(field, "has an invalid value or format"))))
				.orElseGet(() -> Problem.response(Status.BAD_REQUEST, Problem.MALFORMED_BODY, uri.getPath()));
	}

	@ServerExceptionMapper
	public Response unreadableBody(JsonProcessingException exception, UriInfo uri) {
		return Problem.response(Status.BAD_REQUEST, Problem.MALFORMED_BODY, uri.getPath());
	}

	@ServerExceptionMapper
	public Response clientError(WebApplicationException exception, UriInfo uri) {
		Response response = exception.getResponse();
		if (response.getStatus() >= 500) {
			return response;
		}
		Status status = Status.fromStatusCode(response.getStatus());
		String detail = status == Status.BAD_REQUEST ? Problem.MALFORMED_BODY : status.getReasonPhrase();
		return Problem.response(status, detail, uri.getPath());
	}

}

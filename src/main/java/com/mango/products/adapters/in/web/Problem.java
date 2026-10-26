package com.mango.products.adapters.in.web;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.ws.rs.core.Response;

@JsonInclude(JsonInclude.Include.NON_NULL)
record Problem(String title, int status, String detail, String instance, List<FieldViolation> errors) {

	static final String MEDIA_TYPE = "application/problem+json";

	record FieldViolation(String field, String message) {
	}

	static Response response(Response.Status status, String detail, String instance) {
		return response(status, detail, instance, null);
	}

	static Response response(Response.Status status, String detail, String instance, List<FieldViolation> errors) {
		Problem problem = new Problem(status.getReasonPhrase(), status.getStatusCode(), detail, instance, errors);
		return Response.status(status).type(MEDIA_TYPE).entity(problem).build();
	}

}

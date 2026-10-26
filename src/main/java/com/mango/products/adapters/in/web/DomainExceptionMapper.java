package com.mango.products.adapters.in.web;

import java.util.List;

import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import com.mango.products.adapters.in.web.Problem.FieldViolation;
import com.mango.products.domain.DomainException;
import com.mango.products.domain.DomainValidationException;
import com.mango.products.domain.PriceNotFoundException;
import com.mango.products.domain.PriceOverlapException;
import com.mango.products.domain.ProductChangedException;
import com.mango.products.domain.ProductNotFoundException;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.UriInfo;

public class DomainExceptionMapper {

	@ServerExceptionMapper
	public Response toProblem(DomainException exception, UriInfo uri) {
		String path = uri.getPath();
		return switch (exception) {
			case DomainValidationException e -> Problem.response(Status.BAD_REQUEST, Problem.VALIDATION_FAILED, path,
					List.of(new FieldViolation(e.field(), e.getMessage())));
			case ProductNotFoundException e -> Problem.response(Status.NOT_FOUND, e.getMessage(), path);
			case PriceNotFoundException e -> Problem.response(Status.NOT_FOUND, e.getMessage(), path);
			case PriceOverlapException e -> Problem.response(Status.CONFLICT, e.getMessage(), path);
			case ProductChangedException e -> Problem.response(Status.PRECONDITION_FAILED, e.getMessage(), path);
		};
	}

}

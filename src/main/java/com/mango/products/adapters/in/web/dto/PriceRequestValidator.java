package com.mango.products.adapters.in.web.dto;

import java.util.Currency;

import com.mango.products.domain.DateRange;
import com.mango.products.domain.Money;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PriceRequestValidator implements ConstraintValidator<ValidPrice, AddPriceRequest> {

	@Override
	public boolean isValid(AddPriceRequest request, ConstraintValidatorContext context) {
		if (request == null) {
			return true;
		}
		context.disableDefaultConstraintViolation();
		boolean valid = true;

		if (request.initDate() != null && !DateRange.isValid(request.initDate(), request.endDate())) {
			valid = reject(context, "endDate", "must be after initDate and not after " + DateRange.OPEN_END);
		}

		Currency currency = request.currencyOrDefault();
		if (!Money.hasMinorUnit(currency)) {
			valid = reject(context, "currency", currency + " has no minor unit");
		}
		else if (request.value() != null && !Money.fitsInMinorUnits(request.value(), currency)) {
			valid = reject(context, "value", "must have at most " + Money.MAX_DIGITS + " digits in " + currency
					+ " minor units");
		}
		else if (request.value() != null && !Money.isRepresentable(request.value(), currency)) {
			valid = reject(context, "value", "must have at most " + currency.getDefaultFractionDigits()
					+ " decimals for " + currency);
		}
		return valid;
	}

	private static boolean reject(ConstraintValidatorContext context, String field, String message) {
		context.buildConstraintViolationWithTemplate(message).addPropertyNode(field).addConstraintViolation();
		return false;
	}

}

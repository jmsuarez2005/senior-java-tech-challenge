package com.mango.products.adapters.in.web.dto;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Cross-field rules of a new price: the period is ordered and the value fits the currency's minor unit.
 * Each violation is reported on the field it belongs to.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PriceRequestValidator.class)
public @interface ValidPrice {

	String message() default "invalid price";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}

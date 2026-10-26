package com.mango.products.adapters.in.web;

import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mango.products.domain.DomainValidationException;
import com.mango.products.domain.Product;

import jakarta.ws.rs.core.EntityTag;

final class VersionTag {

	private static final Pattern STRONG_TAG = Pattern.compile("\"(\\d{1,18})\"");
	private static final String ANY = "*";

	private VersionTag() {
	}

	static EntityTag of(Product product) {
		return new EntityTag(Long.toString(product.version()));
	}

	static OptionalLong expectedVersion(String ifMatch) {
		if (ifMatch == null || ifMatch.isBlank() || ifMatch.strip().equals(ANY)) {
			return OptionalLong.empty();
		}
		Matcher tag = STRONG_TAG.matcher(ifMatch.strip());
		if (!tag.matches()) {
			throw new DomainValidationException("If-Match", "must be one ETag as returned by GET, such as \"3\"");
		}
		return OptionalLong.of(Long.parseLong(tag.group(1)));
	}

}

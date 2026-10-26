package com.mango.products.adapters.in.web;

// OAuth 2.0 client credentials: services get a token from the authorization server and send it as a bearer token.
final class Scopes {

	static final String SCHEME = "oauth2";
	static final String WRITE = "products:write";
	static final String TOKEN_URL = "http://localhost:8180/realms/products/protocol/openid-connect/token";

	private Scopes() {
	}

}

package com.example.micronaut.sandbox.controller;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.runtime.http.scope.RequestScope;
import jakarta.inject.Inject;

import java.util.Optional;

@RequestScope
class HttpRequestResolver {

	@Inject
	HttpRequestResolver() {
		System.out.println("HttpRequestResolver#init");
	}

	<BODY> Optional<HttpRequest<BODY>> get() {
		return ServerRequestContext.currentRequest();
	}
}

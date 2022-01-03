package com.example.micronaut.sandbox.controller;

import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

@Controller
class FooController {

	private final Provider<RequestScopeBean> service;

	@Inject
	FooController(Provider<RequestScopeBean> service) {
		this.service = service;
	}

	@Get("/wait-for-singleton-monitor")
	@Produces(MediaType.TEXT_PLAIN)
	String acquiresWLockAndWaitsForSingletonMonitor() {
		System.out.println("FooController/wait-for-singleton-monitor");
		System.out.println(Thread.currentThread().getName());
		return "Hello %s!".formatted(service.get().getValue());
	}
}

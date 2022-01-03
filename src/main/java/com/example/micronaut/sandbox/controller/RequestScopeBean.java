package com.example.micronaut.sandbox.controller;

import io.micronaut.runtime.http.scope.RequestScope;
import jakarta.inject.Inject;

/**
 * Request scope holds write lock to create this bean while it is being constructed.
 * <p>
 * The constructor is slow and internally depends on singleton bean for which a synchronization against
 * singleton-objects lock is needed. This emulates coincidental timing between two threads.
 * <p>
 * Constructor is being called lazily as this bean is replaced by prxy in {@link FooController}.
 */
@RequestScope
class RequestScopeBean {

	private final HttpRequestResolver requestResolver;

	@Inject
	RequestScopeBean(HttpRequestResolver requestResolver) throws InterruptedException {
		Thread.sleep(1000); // sleep in RequestScope write lock
		System.out.println("RequestScopeBean init");

		// resolution of this bean needs to be blocked by singleton scope lock from another thread
		this.requestResolver = requestResolver;
	}

	public String getValue() {
		var request = requestResolver.get().orElseThrow();
		return "%s %s %s".formatted(request.getMethod(), request.getPath(), request.hashCode());
	}
}

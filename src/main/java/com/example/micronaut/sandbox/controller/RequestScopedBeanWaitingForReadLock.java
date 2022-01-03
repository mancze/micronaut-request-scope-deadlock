package com.example.micronaut.sandbox.controller;

import io.micronaut.runtime.http.scope.RequestScope;
import jakarta.inject.Inject;

@RequestScope
class RequestScopedBeanWaitingForReadLock {

	private final String value;

	@Inject
	RequestScopedBeanWaitingForReadLock() {
		System.out.println("RequestScopedBeanWaitingForReadLock init");
		value = "OK";
	}

	String getValue() {
		return value;
	}
}

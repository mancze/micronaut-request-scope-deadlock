package com.example.micronaut.sandbox.controller;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

@Factory
class PrototypeFactory {

	@Inject
	PrototypeFactory() {
	}

	@Prototype
	PrototypeBean create(Provider<RequestScopedBeanWaitingForReadLock> beanProvider) {
		System.out.println("PrototypeFactory#create");
		return new PrototypeBean(beanProvider.get().getValue());
	}
}

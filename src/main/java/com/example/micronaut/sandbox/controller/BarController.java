package com.example.micronaut.sandbox.controller;

import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

@Controller
class BarController {

	private final Provider<PrototypeBean> prototypeBeanProvider;

	@Inject
	BarController(Provider<PrototypeBean> beanProvider) {
		this.prototypeBeanProvider = beanProvider;
	}

	@Get("/wait-for-r-lock")
	@Produces(MediaType.TEXT_PLAIN)
	String acquiresSingletonMonitorAndWaitsForRLock() throws InterruptedException {
		System.out.println("BarController/wait-for-r-lock");
		System.out.println(Thread.currentThread().getName());
		Thread.sleep(100);
		return prototypeBeanProvider.get().value();
	}
}

package com.example.micronaut.sandbox.controller;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.regex.Pattern;

@MicronautTest
@Property(name = "micronaut.server.port", value = "8080")
class BeanInitializationDeadlockSpec {
	@Test
	void deadlockTest() throws IOException, InterruptedException {
		Runtime.getRuntime().exec("curl http://localhost:8080/wait-for-r-lock").waitFor();

		Process foo = Runtime.getRuntime().exec("curl http://localhost:8080/wait-for-singleton-monitor");
		Process bar = Runtime.getRuntime().exec("curl http://localhost:8080/wait-for-r-lock");

		Assertions.assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
			var fooLine = foo.inputReader().readLine();
			var barLine = bar.inputReader().readLine();

			System.out.println(fooLine);
			System.out.println(barLine);

			Assertions.assertTrue(Pattern.matches("Hello GET /wait-for-singleton-monitor \\d+!", fooLine));
			Assertions.assertEquals("OK", barLine);
		});
	}
}

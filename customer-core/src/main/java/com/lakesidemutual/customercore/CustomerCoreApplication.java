package com.lakesidemutual.customercore;

import org.microserviceapipatterns.domaindrivendesign.BoundedContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.core.SpringVersion;

/**
 * CustomerCoreApplication is the execution entry point of the Customer Core which
 * is one of the functional/system/application Bounded Contexts of Lakeside Mutual.
 */
@EnableCaching
@SpringBootApplication
public class CustomerCoreApplication implements BoundedContext {
	private static Logger logger = LoggerFactory.getLogger(CustomerCoreApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(CustomerCoreApplication.class, args);
		logger.info("Running with Spring Boot v{}, Spring v{}", SpringBootVersion.getVersion(), SpringVersion.getVersion());
		logger.info("--- Customer Core started ---");
	}
}

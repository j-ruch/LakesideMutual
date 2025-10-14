package com.lakesidemutual.customerselfservice;

import org.microserviceapipatterns.domaindrivendesign.BoundedContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.core.SpringVersion;

/**
 * CustomerSelfServiceApplication is the execution entry point of the Customer Self-Service backend which
 * is one of the functional/system/application Bounded Contexts of Lakeside Mutual.
 */
@EnableCaching
@SpringBootApplication
public class CustomerSelfServiceApplication implements BoundedContext {
	private static Logger logger = LoggerFactory.getLogger(CustomerSelfServiceApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(CustomerSelfServiceApplication.class, args);
		logger.info("Running with Spring Boot v{}, Spring v{}", SpringBootVersion.getVersion(), SpringVersion.getVersion());
		logger.info("--- Customer Self-Service backend started ---");
	}
}

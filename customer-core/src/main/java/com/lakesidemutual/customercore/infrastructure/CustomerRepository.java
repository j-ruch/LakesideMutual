package com.lakesidemutual.customercore.infrastructure;

import com.lakesidemutual.customercore.domain.customer.CustomerAggregateRoot;
import com.lakesidemutual.customercore.domain.customer.CustomerId;
import org.microserviceapipatterns.domaindrivendesign.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * The CustomerRepository can be used to read and write CustomerAggregateRoot objects from and to the backing database. Spring automatically
 * searches for interfaces that extend the JpaRepository interface and creates a corresponding Spring bean for each of them. For more information
 * on repositories visit the <a href="https://docs.spring.io/spring-data/jpa/docs/current/reference/html/">Spring Data JPA - Reference Documentation</a>.
 * */
public interface CustomerRepository extends MongoRepository<CustomerAggregateRoot, CustomerId>, Repository {

	default CustomerId nextId() {
		return CustomerId.random();
	}

	Page<CustomerAggregateRoot> findByCustomerProfileFirstnameContainingOrCustomerProfileLastnameContaining(
			String firstname,
			String lastname,
			Pageable pageable
	);
}

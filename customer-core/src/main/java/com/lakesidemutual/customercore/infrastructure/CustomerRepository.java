package com.lakesidemutual.customercore.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import com.lakesidemutual.customercore.domain.customer.CustomerAggregateRoot;
import com.lakesidemutual.customercore.domain.customer.CustomerId;
import org.microserviceapipatterns.domaindrivendesign.Repository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

/**
 * The CustomerRepository can be used to read and write CustomerAggregateRoot objects from and to the backing database. Spring automatically
 * searches for interfaces that extend the JpaRepository interface and creates a corresponding Spring bean for each of them. For more information
 * on repositories visit the <a href="https://docs.spring.io/spring-data/jpa/docs/current/reference/html/">Spring Data JPA - Reference Documentation</a>.
 * */
public interface CustomerRepository extends JpaRepository<CustomerAggregateRoot, CustomerId>, Repository {

	default CustomerId nextId() {
		return CustomerId.random();
	}

	@Override
	@Query("SELECT customerAggreagateRoot FROM CustomerAggregateRoot customerAggreagateRoot " +
			"LEFT JOIN FETCH customerAggreagateRoot.customerProfile " +
			"LEFT JOIN FETCH customerAggreagateRoot.customerProfile.currentAddress " +
			"LEFT JOIN FETCH customerAggreagateRoot.customerProfile.moveHistory " +
			"WHERE customerAggreagateRoot.id = :customerId")
	Optional<CustomerAggregateRoot> findById(CustomerId customerId);
}

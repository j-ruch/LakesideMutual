package com.lakesidemutual.policymanagement.infrastructure;

import java.util.List;

import org.microserviceapipatterns.domaindrivendesign.Repository;
import org.springframework.data.jpa.repository.JpaRepository;

import com.lakesidemutual.policymanagement.domain.customer.CustomerId;
import com.lakesidemutual.policymanagement.domain.policy.PolicyAggregateRoot;
import com.lakesidemutual.policymanagement.domain.policy.PolicyId;
import org.springframework.data.jpa.repository.Query;

/**
 * The PolicyRepository can be used to read and write PolicyAggregateRoot objects from and to the backing database. Spring automatically
 * searches for interfaces that extend the JpaRepository interface and creates a corresponding Spring bean for each of them. For more information
 * on repositories visit the <a href="https://docs.spring.io/spring-data/jpa/docs/current/reference/html/">Spring Data JPA - Reference Documentation</a>.
 * */
public interface PolicyRepository extends JpaRepository<PolicyAggregateRoot, PolicyId>, Repository {
	default PolicyId nextId() {
		return PolicyId.random();
	}

	@Query("SELECT policyAggergateRoot FROM PolicyAggregateRoot policyAggergateRoot " +
			"LEFT JOIN FETCH policyAggergateRoot.insuringAgreement " +
			"LEFT JOIN FETCH policyAggergateRoot.insuringAgreement.agreementItems " +
			"WHERE policyAggergateRoot.customerId = ?1 ORDER BY policyAggergateRoot.creationDate DESC")
	public List<PolicyAggregateRoot> findAllByCustomerIdOrderByCreationDateDesc(CustomerId customerId);
}

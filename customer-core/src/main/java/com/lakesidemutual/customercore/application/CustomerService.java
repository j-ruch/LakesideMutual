package com.lakesidemutual.customercore.application;

import com.lakesidemutual.customercore.domain.customer.*;
import com.lakesidemutual.customercore.infrastructure.CustomerRepository;
import org.microserviceapipatterns.domaindrivendesign.ApplicationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;


/**
 * The CustomerService class is an application service that is
 * responsible for creating, updating and retrieving customer entities.
 */
@Component
public class CustomerService implements ApplicationService {
	@Autowired
	private CustomerRepository customerRepository;

	@Autowired
	private CustomerFactory customerFactory;

	public Optional<CustomerAggregateRoot> updateAddress(CustomerId customerId, Address updatedAddress) {
		Optional<CustomerAggregateRoot> optCustomer = customerRepository.findById(customerId);
		if (optCustomer.isEmpty()) {
			return optCustomer;
		}

		CustomerAggregateRoot customer = optCustomer.get();
		customer.moveToAddress(updatedAddress);
		customerRepository.save(customer);
		return optCustomer;
	}

	public Optional<CustomerAggregateRoot> updateCustomerProfile(CustomerId customerId, CustomerProfileEntity updatedCustomerProfile) {
		Optional<CustomerAggregateRoot> optCustomer = customerRepository.findById(customerId);
		if (optCustomer.isEmpty()) {
			return optCustomer;
		}

		CustomerAggregateRoot customer = optCustomer.get();
		customer.updateCustomerProfile(updatedCustomerProfile);
		customerRepository.save(customer);
		return optCustomer;
	}

	public CustomerAggregateRoot createCustomer(CustomerProfileEntity customerProfile) {
		CustomerAggregateRoot customer = customerFactory.create(customerProfile);
		customerRepository.save(customer);
		return customer;
	}

	public List<CustomerAggregateRoot> getCustomers(String ids) {
		List<CustomerId> customerIds = Arrays.stream(ids.split(",")).map(id -> new CustomerId(id.trim())).toList();

		List<CustomerAggregateRoot> customers = new ArrayList<>();
		for (CustomerId customerId : customerIds) {
			Optional<CustomerAggregateRoot> customer = customerRepository.findById(customerId);
			customer.ifPresent(customers::add);
		}
		return customers;
	}

public Page<CustomerAggregateRoot> getCustomers(String filter, int limit, int offset) {
	    PageRequest pageRequest = PageRequest.of(offset / limit, limit);
	    org.springframework.data.domain.Page<CustomerAggregateRoot> page = customerRepository
	        .findByCustomerProfileFirstnameContainingOrCustomerProfileLastnameContaining(
	            filter, filter, pageRequest);
	    return new Page<>(page.getContent(), offset, limit, (int) page.getTotalElements());
	}
}

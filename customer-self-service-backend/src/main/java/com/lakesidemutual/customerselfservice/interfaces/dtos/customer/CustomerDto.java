package com.lakesidemutual.customerselfservice.interfaces.dtos.customer;

import com.lakesidemutual.customercore.grpc.CustomerCoreProtoResponseDto;
import com.lakesidemutual.customercore.grpc.CustomerProfileProtoDto;
import org.springframework.hateoas.RepresentationModel;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.time.Instant;
import java.util.Date;
import java.util.Set;

/**
 * The CustomerDto class is a data transfer object (DTO) that represents a single customer.
 * It inherits from the ResourceSupport class which allows us to create a REST representation (e.g., JSON, XML)
 * that follows the HATEOAS principle. For example, links can be added to the representation (e.g., self, address.change)
 * which means that future actions the client may take can be discovered from the resource representation.
 *
 * @see <a href="https://docs.spring.io/spring-hateoas/docs/current/reference/html/">Spring HATEOAS - Reference Documentation</a>
 */
public class CustomerDto extends RepresentationModel {
	private String customerId;
	@JsonUnwrapped
	private CustomerProfileDto customerProfile;

	public CustomerDto() {
	}

	public CustomerDto(Set<String> includedFields, CustomerCoreProtoResponseDto customer) {
		this.customerId = select(includedFields, "customerId", customer.getCustomerId());

		final CustomerProfileProtoDto profile = customer.getCustomerProfile();
		this.customerProfile = new CustomerProfileDto();
		this.customerProfile.setCurrentAddress(new AddressDto());
		this.customerProfile.setFirstname(select(includedFields, "firstname", profile.getFirstname()));
		this.customerProfile.setLastname(select(includedFields, "lastname", profile.getLastname()));
		this.customerProfile.setBirthday(select(includedFields, "birthday", Date.from(Instant.ofEpochSecond(profile.getBirthday().getSeconds()))));
		this.customerProfile.getCurrentAddress().setStreetAddress(select(includedFields, "streetAddress", profile.getAddress().getStreetAddress()));
		this.customerProfile.getCurrentAddress().setPostalCode(select(includedFields, "postalCode", profile.getAddress().getPostalCode()));
		this.customerProfile.getCurrentAddress().setCity(select(includedFields, "city", profile.getAddress().getCity()));
		this.customerProfile.setEmail(select(includedFields, "email", profile.getEmail()));
		this.customerProfile.setPhoneNumber(select(includedFields, "phoneNumber", profile.getPhoneNumber()));
		this.customerProfile.setMoveHistory(select(includedFields, "moveHistory", profile.getMoveHistoryList().stream().map(addressProtoDto -> {
			AddressDto addressDto = new AddressDto();
			addressDto.setStreetAddress(addressProtoDto.getStreetAddress());
			addressDto.setPostalCode(addressProtoDto.getPostalCode());
			addressDto.setCity(addressProtoDto.getCity());
			return addressDto;
		}).toList()));
	}

	private static <T> T select(Set<String> includedFields, String fieldName, T value) {
		if(includedFields.isEmpty() || includedFields.contains(fieldName)) {
			return value;
		} else {
			return null;
		}
	}


	public String getCustomerId() {
		return customerId;
	}

	public CustomerProfileDto getCustomerProfile() {
		return this.customerProfile;
	}

	public void setCustomerId(String customerId) {
		this.customerId = customerId;
	}

	public void setCustomerProfile(CustomerProfileDto customerProfile) {
		this.customerProfile = customerProfile;
	}
}

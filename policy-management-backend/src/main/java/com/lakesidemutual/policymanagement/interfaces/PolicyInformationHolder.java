package com.lakesidemutual.policymanagement.interfaces;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lakesidemutual.policymanagement.domain.customer.CustomerId;
import com.lakesidemutual.policymanagement.domain.policy.DeletePolicyEvent;
import com.lakesidemutual.policymanagement.domain.policy.InsuringAgreementEntity;
import com.lakesidemutual.policymanagement.domain.policy.MoneyAmount;
import com.lakesidemutual.policymanagement.domain.policy.PolicyAggregateRoot;
import com.lakesidemutual.policymanagement.domain.policy.PolicyId;
import com.lakesidemutual.policymanagement.domain.policy.PolicyPeriod;
import com.lakesidemutual.policymanagement.domain.policy.PolicyType;
import com.lakesidemutual.policymanagement.domain.policy.UpdatePolicyEvent;
import com.lakesidemutual.policymanagement.infrastructure.CustomerCoreRemoteProxy;
import com.lakesidemutual.policymanagement.infrastructure.PolicyRepository;
import com.lakesidemutual.policymanagement.infrastructure.RiskManagementMessageProducer;
import com.lakesidemutual.policymanagement.interfaces.dtos.UnknownCustomerException;
import com.lakesidemutual.policymanagement.interfaces.dtos.customer.CustomerDto;
import com.lakesidemutual.policymanagement.interfaces.dtos.policy.CreatePolicyRequestDto;
import com.lakesidemutual.policymanagement.interfaces.dtos.policy.PaginatedPolicyResponseDto;
import com.lakesidemutual.policymanagement.interfaces.dtos.policy.PolicyDto;
import com.lakesidemutual.policymanagement.interfaces.dtos.policy.PolicyNotFoundException;

/**
 * This REST controller gives clients access to the insurance policies. It is an example of the
 * <i>Information Holder Resource</i> pattern. This particular one is a special type of information holder called <i>Master Data Holder</i>.
 *
 * @see <a href="https://www.microservice-api-patterns.org/patterns/responsibility/endpointRoles/InformationHolderResource">Information Holder Resource</a>
 * @see <a href="https://www.microservice-api-patterns.org/patterns/responsibility/informationHolderEndpointTypes/MasterDataHolder">Master Data Holder</a>
 */
@RestController
@RequestMapping("/policies")
public class PolicyInformationHolder {
	private final Logger logger = LoggerFactory.getLogger(this.getClass());

	@Autowired
	private PolicyRepository policyRepository;

	@Autowired
	private RiskManagementMessageProducer riskManagementMessageProducer;

	@Autowired
	private CustomerCoreRemoteProxy customerCoreRemoteProxy;

	@Operation(summary = "Create a new policy.")
	@PostMapping
	public ResponseEntity<PolicyDto> createPolicy(
			@Parameter(description = "the policy that is to be added", required = true)
			@Valid
			@RequestBody
			CreatePolicyRequestDto createPolicyDto,
			HttpServletRequest request) {
		logger.info("Method createPolicy started.");
		String customerIdString = createPolicyDto.getCustomerId();
		logger.info("Creating a new policy for customer with id '{}'", customerIdString);
		CustomerId customerId = new CustomerId(customerIdString);
		logger.info("CustomerId: {}", customerId);
		List<CustomerDto> customers = customerCoreRemoteProxy.getCustomersById(customerId);
		logger.info("CustomerCoreRemoteProxy returned {} customers.", customers.size());
		if(customers.isEmpty()) {
			final String errorMessage = "Failed to find a customer with id '{}'";
			logger.warn(errorMessage, customerId.getId());
			logger.warn("Method createPolicy ended with exception.");
			throw new UnknownCustomerException(errorMessage);
		}

		PolicyId id = PolicyId.random();
		logger.info("Generated PolicyId: {}", id);
		PolicyType policyType = new PolicyType(createPolicyDto.getPolicyType());
		PolicyPeriod policyPeriod = createPolicyDto.getPolicyPeriod().toDomainObject();
		MoneyAmount deductible = createPolicyDto.getDeductible().toDomainObject();
		MoneyAmount policyLimit = createPolicyDto.getPolicyLimit().toDomainObject();
		MoneyAmount insurancePremium = createPolicyDto.getInsurancePremium().toDomainObject();
		InsuringAgreementEntity insuringAgreement = createPolicyDto.getInsuringAgreement().toDomainObject();
		logger.info("PolicyType: {}, PolicyPeriod: {}, Deductible: {}, PolicyLimit: {}, InsurancePremium: {}, InsuringAgreement: {}",
			policyType, policyPeriod, deductible, policyLimit, insurancePremium, insuringAgreement);
		PolicyAggregateRoot policy = new PolicyAggregateRoot(id, customerId, new Date(), policyPeriod, policyType, deductible, policyLimit, insurancePremium, insuringAgreement);
		logger.info("Saving new policy to repository.");
		policyRepository.save(policy);

		CustomerDto customer = customers.get(0);
		PolicyDto policyDto = createPolicyDtos(Arrays.asList(policy), "").get(0);
		final UpdatePolicyEvent event = new UpdatePolicyEvent(request.getRemoteAddr(), new Date(), customer, policyDto);
		logger.info("Emitting UpdatePolicyEvent to Risk Management backend.");
		riskManagementMessageProducer.emitEvent(event);
		logger.info("Returning created PolicyDto to client.");
		logger.info("Method createPolicy ended.");
		return ResponseEntity.ok(policyDto);
	}

	@Operation(summary = "Update an existing policy.")
	@PutMapping(value = "/{policyId}")
	public ResponseEntity<PolicyDto> updatePolicy(
			@Parameter(description = "the policy's unique id", required = true) @PathVariable PolicyId policyId,
			@Parameter(description = "the updated policy", required = true) @Valid @RequestBody CreatePolicyRequestDto createPolicyDto,
			HttpServletRequest request) {
		logger.info("Method updatePolicy started for policyId '{}'.", policyId.getId());
		logger.info("Updating policy with id '{}'", policyId.getId());

		Optional<PolicyAggregateRoot> optPolicy = policyRepository.findById(policyId);
		logger.info("PolicyRepository.findById returned present: {}", optPolicy.isPresent());
		if(!optPolicy.isPresent()) {
			final String errorMessage = "Failed to find a policy with id '{}'";
			logger.warn(errorMessage, policyId.getId());
			logger.warn("Method updatePolicy ended with exception.");
			throw new PolicyNotFoundException(errorMessage);
		}

		CustomerId customerId = new CustomerId(createPolicyDto.getCustomerId());
		logger.info("CustomerId: {}", customerId);
		List<CustomerDto> customers = customerCoreRemoteProxy.getCustomersById(customerId);
		logger.info("CustomerCoreRemoteProxy returned {} customers.", customers.size());
		if(customers.isEmpty()) {
			final String errorMessage = "Failed to find a customer with id '{}'";
			logger.warn(errorMessage, customerId.getId());
			logger.warn("Method updatePolicy ended with exception.");
			throw new UnknownCustomerException(errorMessage);
		}

		PolicyType policyType = new PolicyType(createPolicyDto.getPolicyType());
		PolicyPeriod policyPeriod = createPolicyDto.getPolicyPeriod().toDomainObject();
		MoneyAmount deductible = createPolicyDto.getDeductible().toDomainObject();
		MoneyAmount policyLimit = createPolicyDto.getPolicyLimit().toDomainObject();
		MoneyAmount insurancePremium = createPolicyDto.getInsurancePremium().toDomainObject();
		InsuringAgreementEntity insuringAgreement = createPolicyDto.getInsuringAgreement().toDomainObject();
		logger.info("PolicyType: {}, PolicyPeriod: {}, Deductible: {}, PolicyLimit: {}, InsurancePremium: {}, InsuringAgreement: {}",
			policyType, policyPeriod, deductible, policyLimit, insurancePremium, insuringAgreement);

		PolicyAggregateRoot policy = optPolicy.get();
		logger.info("Loaded PolicyAggregateRoot: {}", policy);
		policy.setPolicyPeriod(policyPeriod);
		policy.setPolicyType(policyType);
		policy.setDeductible(deductible);
		policy.setPolicyLimit(policyLimit);
		policy.setInsurancePremium(insurancePremium);
		policy.setInsuringAgreement(insuringAgreement);
		logger.info("Saving updated policy to repository.");
		policyRepository.save(policy);

		CustomerDto customer = customers.get(0);
		PolicyDto policyDto = createPolicyDtos(Arrays.asList(policy), "").get(0);
		final UpdatePolicyEvent event = new UpdatePolicyEvent(request.getRemoteAddr(), new Date(), customer, policyDto);
		logger.info("Emitting UpdatePolicyEvent to Risk Management backend.");
		riskManagementMessageProducer.emitEvent(event);

		PolicyDto response = createPolicyDtos(Arrays.asList(policy), "").get(0);
		logger.info("Returning updated PolicyDto to client.");
		logger.info("Method updatePolicy ended.");
		return ResponseEntity.ok(response);
	}

	@Operation(summary = "Delete an existing policy.")
	@DeleteMapping(value = "/{policyId}")
	public ResponseEntity<Void> deletePolicy(
			@Parameter(description = "the policy's unique id", required = true) @PathVariable PolicyId policyId,
			HttpServletRequest request) {
		logger.info("Method deletePolicy started for policyId '{}'.", policyId.getId());
		logger.info("Deleting policy with id '{}'", policyId.getId());
		policyRepository.deleteById(policyId);
		logger.info("Policy with id '{}' deleted.", policyId.getId());

		final DeletePolicyEvent event = new DeletePolicyEvent(request.getRemoteAddr(), new Date(), policyId.getId());
		logger.info("Emitting DeletePolicyEvent to Risk Management backend.");
		riskManagementMessageProducer.emitEvent(event);

		logger.info("Returning noContent response.");
		logger.info("Method deletePolicy ended.");
		return ResponseEntity.noContent().build();
	}

	private List<PolicyDto> createPolicyDtos(List<PolicyAggregateRoot> policies, String expand) {
		logger.info("Method createPolicyDtos started. expand='{}'", expand);
		List<CustomerDto> customers = null;
		if(expand.equals("customer")) {
			logger.info("Expanding customer information for policies.");
			List<CustomerId> customerIds = policies.stream().map(p -> p.getCustomerId()).collect(Collectors.toList());
			customers = customerCoreRemoteProxy.getCustomersById(customerIds.toArray(new CustomerId[customerIds.size()]));
			logger.info("CustomerCoreRemoteProxy returned {} customers.", customers.size());
		}

		List<PolicyDto> policyDtos = new ArrayList<>();
		for(int i = 0; i < policies.size(); i++) {
			PolicyAggregateRoot policy = policies.get(i);
			logger.info("Processing policy with id '{}'.", policy.getId().getId());
			PolicyDto policyDto = PolicyDto.fromDomainObject(policy);
			if(customers != null) {
				CustomerDto customer = customers.get(i);
				logger.info("Setting customer for policy DTO: {}", customer);
				policyDto.setCustomer(customer);
			}
			policyDtos.add(policyDto);
			logger.info("Added PolicyDto for policy id '{}'.", policy.getId().getId());
		}
		logger.info("Method createPolicyDtos ended.");
		return policyDtos;
	}

	private PaginatedPolicyResponseDto createPaginatedPolicyResponseDto(Integer limit, Integer offset, String expand, int size,
			List<PolicyDto> policyDtos) {
		logger.info("Method createPaginatedPolicyResponseDto started. limit={}, offset={}, expand={}, size={}", limit, offset, expand, size);
		PaginatedPolicyResponseDto paginatedPolicyResponseDto = new PaginatedPolicyResponseDto(limit, offset,
				size, policyDtos);

		paginatedPolicyResponseDto.add(linkTo(methodOn(PolicyInformationHolder.class).getPolicies(limit, offset, expand)).withSelfRel());

		if (offset > 0) {
			logger.info("Adding prev link to paginated response.");
			paginatedPolicyResponseDto.add(linkTo(
					methodOn(PolicyInformationHolder.class).getPolicies(limit, Math.max(0, offset - limit), expand))
					.withRel("prev"));
		}

		if (offset < size - limit) {
			logger.info("Adding next link to paginated response.");
			paginatedPolicyResponseDto.add(linkTo(methodOn(PolicyInformationHolder.class).getPolicies(limit, offset + limit, expand))
					.withRel("next"));
		}

		logger.info("Method createPaginatedPolicyResponseDto ended.");
		return paginatedPolicyResponseDto;
	}

	@Operation(summary = "Get all policies, newest first.")
	@GetMapping
	public ResponseEntity<PaginatedPolicyResponseDto> getPolicies(
			@Parameter(description = "the maximum number of policies per page", required = false) @RequestParam(value = "limit", required = false, defaultValue = "10") Integer limit,
			@Parameter(description = "the offset of the page's first policy", required = false) @RequestParam(value = "offset", required = false, defaultValue = "0") Integer offset,
			@Parameter(description = "a comma-separated list of the fields that should be expanded in the response", required = false) @RequestParam(value = "expand", required = false, defaultValue = "") String expand) {
		logger.info("Method getPolicies started. offset={}, limit={}, expand={}", offset, limit, expand);
		logger.info("Fetching a page of policies (offset={},limit={},fields='{}')", offset, limit, expand);
		List<PolicyAggregateRoot> allPolicies = policyRepository.findAll(Sort.by(Sort.Direction.DESC, PolicyAggregateRoot.FIELD_CREATION_DATE));
		logger.info("Fetched {} policies from repository.", allPolicies.size());
		allPolicies.forEach(policy -> logger.info("Found policy with id '{}' for customer with id '{}'", policy.getId().getId(), policy.getCustomerId().getId()));
		List<PolicyAggregateRoot> policies = allPolicies.stream().skip(offset).limit(limit).collect(Collectors.toList());
		logger.info("Selected {} policies for current page.", policies.size());
		policies.forEach(policy -> logger.info("Including policy with id '{}' for customer with id '{}'", policy.getId().getId(), policy.getCustomerId().getId()));
		List<PolicyDto> policyDtos = createPolicyDtos(policies, expand);
		policyDtos.forEach(policy -> logger.info("Created DTO for policy with id '{}'", policy.getPolicyId()));
		PaginatedPolicyResponseDto paginatedPolicyResponse = createPaginatedPolicyResponseDto(limit, offset, expand, allPolicies.size(), policyDtos);
		logger.info("Returning paginated policy response");
		logger.info("Method getPolicies ended.");
		return ResponseEntity.ok(paginatedPolicyResponse);
	}

	@Operation(summary = "Get a single policy.")
	@GetMapping(value = "/{policyId}")
	public ResponseEntity<PolicyDto> getPolicy(
			@Parameter(description = "the policy's unique id", required = true) @PathVariable PolicyId policyId,
			@Parameter(description = "a comma-separated list of the fields that should be expanded in the response", required = false) @RequestParam(value = "expand", required = false, defaultValue = "") String expand) {
		logger.info("Method getPolicy started for policyId '{}', expand='{}'.", policyId.getId(), expand);
		Optional<PolicyAggregateRoot> optPolicy = policyRepository.findById(policyId);
		logger.info("PolicyRepository.findById returned present: {}", optPolicy.isPresent());
		if(!optPolicy.isPresent()) {
			final String errorMessage = "Failed to find a policy with id '{}'";
			logger.warn(errorMessage, policyId.getId());
			logger.warn("Method getPolicy ended with exception.");
			throw new PolicyNotFoundException(errorMessage);
		}

		PolicyAggregateRoot policy = optPolicy.get();
		logger.info("Loaded PolicyAggregateRoot: {}", policy);
		PolicyDto response = createPolicyDtos(Arrays.asList(policy), expand).get(0);
		logger.info("Returning PolicyDto for policyId '{}'.", policyId.getId());
		logger.info("Method getPolicy ended.");
		return ResponseEntity.ok(response);
	}
}

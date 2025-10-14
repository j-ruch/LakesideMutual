package com.lakesidemutual.policymanagement.interfaces;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lakesidemutual.policymanagement.domain.insurancequoterequest.InsuranceQuoteEntity;
import com.lakesidemutual.policymanagement.domain.insurancequoterequest.InsuranceQuoteRequestAggregateRoot;
import com.lakesidemutual.policymanagement.domain.insurancequoterequest.InsuranceQuoteResponseEvent;
import com.lakesidemutual.policymanagement.domain.insurancequoterequest.RequestStatus;
import com.lakesidemutual.policymanagement.domain.policy.MoneyAmount;
import com.lakesidemutual.policymanagement.infrastructure.CustomerSelfServiceMessageProducer;
import com.lakesidemutual.policymanagement.infrastructure.InsuranceQuoteRequestRepository;
import com.lakesidemutual.policymanagement.interfaces.dtos.insurancequoterequest.InsuranceQuoteRequestDto;
import com.lakesidemutual.policymanagement.interfaces.dtos.insurancequoterequest.InsuranceQuoteRequestNotFoundException;
import com.lakesidemutual.policymanagement.interfaces.dtos.insurancequoterequest.InsuranceQuoteResponseDto;
import com.lakesidemutual.policymanagement.interfaces.dtos.policy.MoneyAmountDto;

/**
 * This REST controller gives clients access to the insurance quote requests. It is an example of the
 * <i>Information Holder Resource</i> pattern. This particular one is a special type of information holder called <i>Operational Data Holder</i>.
 *
 * @see <a href="https://www.microservice-api-patterns.org/patterns/responsibility/informationHolderEndpointTypes/OperationalDataHolder">Operational Data Holder</a>
 * 
 * As it supports responding to requests, you can also view it as a Processing Resource:
 * 
 *  * @see <a href="https://www.microservice-api-patterns.org/patterns/responsibility/endpointRoles/ProcessingResource">Processing Resource</a>
 *  
 *Matching RDD role stereotypes are <i>Coordinator</i> and <i>Information Holder</i>:
 *
 *  * @see <a href="http://www.wirfs-brock.com/PDFs/A_Brief-Tour-of-RDD.pdf">A Brief Tour of RDD</a>
 */
@RestController
@RequestMapping("/insurance-quote-requests")
public class InsuranceQuoteRequestProcessingResource {
	private final Logger logger = LoggerFactory.getLogger(this.getClass());

	@Autowired
	private InsuranceQuoteRequestRepository insuranceQuoteRequestRepository;

	@Autowired
	private CustomerSelfServiceMessageProducer customerSelfServiceMessageProducer;

	@Operation(summary = "Get all Insurance Quote Requests.")
	@GetMapping
	public ResponseEntity<List<InsuranceQuoteRequestDto>> getInsuranceQuoteRequests() {
		logger.info("Method getInsuranceQuoteRequests started.");
		List<InsuranceQuoteRequestAggregateRoot> quoteRequests = insuranceQuoteRequestRepository.findAllByOrderByDateDesc();
		logger.info("Fetched {} Insurance Quote Requests.", quoteRequests.size());
		quoteRequests.forEach(quote -> logger.info("Found Insurance Quote Request with id '{}' and status '{}'", quote.getId(), quote.getStatus()));
		List<InsuranceQuoteRequestDto> quoteRequestDtos = quoteRequests.stream().map(InsuranceQuoteRequestDto::fromDomainObject).collect(Collectors.toList());
		logger.info("Mapped Insurance Quote Requests to DTOs.");
		quoteRequestDtos.forEach(quote -> logger.info("Created Insurance Quote Request DTO with id '{}'", quote.getId()));
		logger.info("Returning insurance quote request DTOs to client");
		logger.info("Method getInsuranceQuoteRequests ended.");
		return ResponseEntity.ok(quoteRequestDtos);
	}

	@Operation(summary = "Get a specific Insurance Quote Request.")
	@GetMapping(value = "/{id}") /* MAP: Retrieval Operation */
	public ResponseEntity<InsuranceQuoteRequestDto> getInsuranceQuoteRequest(@Parameter(description = "the insurance quote request's unique id", required = true) @PathVariable Long id) {
		logger.info("Method getInsuranceQuoteRequest started with id '{}'.", id);
		Optional<InsuranceQuoteRequestAggregateRoot> optInsuranceQuoteRequest = insuranceQuoteRequestRepository.findById(id);
		logger.info("InsuranceQuoteRequestRepository.findById returned present: {}", optInsuranceQuoteRequest.isPresent());
		if (!optInsuranceQuoteRequest.isPresent()) {
			final String errorMessage = "Failed to find an Insurance Quote Request with id '{}'";
			logger.warn(errorMessage, id);
			logger.warn("Method getInsuranceQuoteRequest ended with exception.");
			throw new InsuranceQuoteRequestNotFoundException(errorMessage);
		}
		logger.info("Found Insurance Quote Request with id '{}'", id);
		InsuranceQuoteRequestAggregateRoot insuranceQuoteRequest = optInsuranceQuoteRequest.get();
		logger.info("Loaded InsuranceQuoteRequestAggregateRoot: {}", insuranceQuoteRequest);
		InsuranceQuoteRequestDto insuranceQuoteRequestDto = InsuranceQuoteRequestDto.fromDomainObject(insuranceQuoteRequest);
		logger.info("Returning Insurance Quote Request DTO with id '{}' to client", id);
		logger.info("Method getInsuranceQuoteRequest ended.");
		return ResponseEntity.ok(insuranceQuoteRequestDto);
	}

	@Operation(summary = "Updates the status of an existing Insurance Quote Request")
	@PatchMapping(value = "/{id}") /* MAP: State Transition Operation */
	public ResponseEntity<InsuranceQuoteRequestDto> respondToInsuranceQuoteRequest(
			@Parameter(description = "the insurance quote request's unique id", required = true) @PathVariable Long id,
			@Parameter(description = "the response that contains a new insurance quote if the request has been accepted", required = true)
			@Valid @RequestBody InsuranceQuoteResponseDto insuranceQuoteResponseDto) {

		logger.info("Method respondToInsuranceQuoteRequest started with id '{}'.", id);
		logger.info("InsuranceQuoteResponseDto: {}", insuranceQuoteResponseDto);

		Optional<InsuranceQuoteRequestAggregateRoot> optInsuranceQuoteRequest = insuranceQuoteRequestRepository.findById(id);
		logger.info("InsuranceQuoteRequestRepository.findById returned present: {}", optInsuranceQuoteRequest.isPresent());
		if (!optInsuranceQuoteRequest.isPresent()) {
			final String errorMessage = "Failed to respond to Insurance Quote Request, because there is no Insurance Quote Request with id '{}'";
			logger.warn(errorMessage, id);
			logger.warn("Method respondToInsuranceQuoteRequest ended with exception.");
			throw new InsuranceQuoteRequestNotFoundException(errorMessage);
		}

		logger.info("Found Insurance Quote Request with id '{}'", id);
		final Date date = new Date();
		final InsuranceQuoteRequestAggregateRoot insuranceQuoteRequest = optInsuranceQuoteRequest.get();
		logger.info("Loaded InsuranceQuoteRequestAggregateRoot: {}", insuranceQuoteRequest);
		if(insuranceQuoteResponseDto.getStatus().equals(RequestStatus.QUOTE_RECEIVED.toString())) {
			logger.info("Insurance Quote Request with id '{}' has been accepted", id);

			Date expirationDate = insuranceQuoteResponseDto.getExpirationDate();
			MoneyAmountDto insurancePremiumDto = insuranceQuoteResponseDto.getInsurancePremium();
			MoneyAmountDto policyLimitDto = insuranceQuoteResponseDto.getPolicyLimit();
			logger.info("ExpirationDate: {}, InsurancePremiumDto: {}, PolicyLimitDto: {}", expirationDate, insurancePremiumDto, policyLimitDto);
			MoneyAmount insurancePremium = insurancePremiumDto.toDomainObject();
			MoneyAmount policyLimit = policyLimitDto.toDomainObject();
			InsuranceQuoteEntity insuranceQuote = new InsuranceQuoteEntity(expirationDate, insurancePremium, policyLimit);
			logger.info("Accepting request for insurance quote.");
			insuranceQuoteRequest.acceptRequest(insuranceQuote, date);
			final InsuranceQuoteResponseEvent insuranceQuoteResponseEvent = new InsuranceQuoteResponseEvent(date, insuranceQuoteRequest.getId(), true, expirationDate, insurancePremiumDto, policyLimitDto);
			logger.info("Sending Insurance Quote Response Event for Insurance Quote Request with id '{}' to Customer Self-Service backend", id);
			customerSelfServiceMessageProducer.sendInsuranceQuoteResponseEvent(insuranceQuoteResponseEvent);
			logger.info("Sent Insurance Quote Response Event for Insurance Quote Request with id '{}' to Customer Self-Service backend", id);
		} else if(insuranceQuoteResponseDto.getStatus().equals(RequestStatus.REQUEST_REJECTED.toString())) {
			logger.info("Insurance Quote Request with id '{}' has been rejected", id);

			insuranceQuoteRequest.rejectRequest(date);
			final InsuranceQuoteResponseEvent insuranceQuoteResponseEvent = new InsuranceQuoteResponseEvent(date, insuranceQuoteRequest.getId(), false, null, null, null);
			logger.info("Sending Insurance Quote Response Event for Insurance Quote Request with id '{}' to Customer Self-Service backend", id);
			customerSelfServiceMessageProducer.sendInsuranceQuoteResponseEvent(insuranceQuoteResponseEvent);
			logger.info("Sent Insurance Quote Response Event for Insurance Quote Request with id '{}' to Customer Self-Service backend", id);
		}
		logger.info("Saving Insurance Quote Request with id '{}' to the repository", id);
		insuranceQuoteRequestRepository.save(insuranceQuoteRequest);
		logger.info("Saved Insurance Quote Request with id '{}' to the repository", id);

		InsuranceQuoteRequestDto insuranceQuoteRequestDto = InsuranceQuoteRequestDto.fromDomainObject(insuranceQuoteRequest);
		logger.info("Returning Insurance Quote Request DTO with id '{}' to client", id);
		logger.info("Method respondToInsuranceQuoteRequest ended.");
		return ResponseEntity.ok(insuranceQuoteRequestDto);
	}
}
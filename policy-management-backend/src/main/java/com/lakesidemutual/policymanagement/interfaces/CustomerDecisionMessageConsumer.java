package com.lakesidemutual.policymanagement.interfaces;

import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import com.lakesidemutual.policymanagement.domain.customer.CustomerId;
import com.lakesidemutual.policymanagement.domain.insurancequoterequest.CustomerDecisionEvent;
import com.lakesidemutual.policymanagement.domain.insurancequoterequest.CustomerInfoEntity;
import com.lakesidemutual.policymanagement.domain.insurancequoterequest.InsuranceQuoteExpiredEvent;
import com.lakesidemutual.policymanagement.domain.insurancequoterequest.InsuranceQuoteRequestAggregateRoot;
import com.lakesidemutual.policymanagement.domain.insurancequoterequest.PolicyCreatedEvent;
import com.lakesidemutual.policymanagement.domain.insurancequoterequest.RequestStatus;
import com.lakesidemutual.policymanagement.domain.policy.InsuringAgreementEntity;
import com.lakesidemutual.policymanagement.domain.policy.MoneyAmount;
import com.lakesidemutual.policymanagement.domain.policy.PolicyAggregateRoot;
import com.lakesidemutual.policymanagement.domain.policy.PolicyId;
import com.lakesidemutual.policymanagement.domain.policy.PolicyPeriod;
import com.lakesidemutual.policymanagement.domain.policy.PolicyType;
import com.lakesidemutual.policymanagement.domain.policy.UpdatePolicyEvent;
import com.lakesidemutual.policymanagement.infrastructure.CustomerCoreRemoteProxy;
import com.lakesidemutual.policymanagement.infrastructure.CustomerSelfServiceMessageProducer;
import com.lakesidemutual.policymanagement.infrastructure.InsuranceQuoteRequestRepository;
import com.lakesidemutual.policymanagement.infrastructure.PolicyRepository;
import com.lakesidemutual.policymanagement.infrastructure.RiskManagementMessageProducer;
import com.lakesidemutual.policymanagement.interfaces.dtos.customer.CustomerDto;
import com.lakesidemutual.policymanagement.interfaces.dtos.policy.PolicyDto;

/**
 * CustomerDecisionMessageConsumer is a Spring component that consumes CustomerDecisionEvents
 * as they arrive through the ActiveMQ message queue. It processes these events by updating the
 * corresponding insurance quote requests.
 * */
@Component
public class CustomerDecisionMessageConsumer {
	private final Logger logger = LoggerFactory.getLogger(this.getClass());

	@Autowired
	private InsuranceQuoteRequestRepository insuranceQuoteRequestRepository;

	@Autowired
	private PolicyRepository policyRepository;

	@Autowired
	private CustomerSelfServiceMessageProducer customerSelfServiceMessageProducer;

	@Autowired
	private RiskManagementMessageProducer riskManagementMessageProducer;

	@Autowired
	private CustomerCoreRemoteProxy customerCoreRemoteProxy;

	@JmsListener(destination = "${customerDecisionEvent.queueName}")
	public void receiveCustomerDecision(final Message<CustomerDecisionEvent> message) {
		logger.info("Method receiveCustomerDecision started.");
		logger.info("Raw JMS message: {}", message);
		final CustomerDecisionEvent customerDecisionEvent = message.getPayload();
		logger.info("CustomerDecisionEvent payload: {}", customerDecisionEvent);
		final Long id = customerDecisionEvent.getInsuranceQuoteRequestId();
		logger.info("Looking up InsuranceQuoteRequestAggregateRoot with id '{}'", id);
		final Optional<InsuranceQuoteRequestAggregateRoot> insuranceQuoteRequestOpt = insuranceQuoteRequestRepository.findById(id);

		if(!insuranceQuoteRequestOpt.isPresent()) {
			logger.warn("Method receiveCustomerDecision ended with error.");
			return;
		}

		logger.info("Processing customer decision event for insurance quote request with id '{}'", id);
		final InsuranceQuoteRequestAggregateRoot insuranceQuoteRequest = insuranceQuoteRequestOpt.get();
		logger.info("Loaded InsuranceQuoteRequestAggregateRoot: {}", insuranceQuoteRequest);
		final Date decisionDate = customerDecisionEvent.getDate();
		logger.info("Decision date: {}", decisionDate);

		if(customerDecisionEvent.isQuoteAccepted()) {
			logger.info("Customer accepted the quote for request '{}'.", id);
			if(insuranceQuoteRequest.getStatus().equals(RequestStatus.QUOTE_EXPIRED) || insuranceQuoteRequest.hasQuoteExpired(decisionDate)) {
				logger.info("The insurance quote for request {} has expired already. Marking the quote as accepted and expired.", insuranceQuoteRequest.getId());
				/*
				 * If the quote has been accepted after it has already expired, we mark the quote as accepted
				 * and expired and send a InsuranceQuoteExpiredEvent back to the Customer Self-Service backend.
				 * */
				Date expirationDate;
				if(insuranceQuoteRequest.getStatus().equals(RequestStatus.QUOTE_EXPIRED)) {
					logger.info("Status is QUOTE_EXPIRED, popping status.");
					expirationDate = insuranceQuoteRequest.popStatus().getDate();
					logger.info("Expiration date from status: {}", expirationDate);
				} else {
					expirationDate = decisionDate;
					logger.info("Expiration date from decision: {}", expirationDate);
				}

				logger.info("Accepting quote for request '{}'.", insuranceQuoteRequest.getId());
				insuranceQuoteRequest.acceptQuote(decisionDate);
				logger.info("Marking quote as expired for request '{}'.", insuranceQuoteRequest.getId());
				insuranceQuoteRequest.markQuoteAsExpired(expirationDate);
				InsuranceQuoteExpiredEvent event = new InsuranceQuoteExpiredEvent(expirationDate, insuranceQuoteRequest.getId());
				logger.info("Sending InsuranceQuoteExpiredEvent for insurance quote request {} to the Customer Self-Service backend.", insuranceQuoteRequest.getId());
				customerSelfServiceMessageProducer.sendInsuranceQuoteExpiredEvent(event);
				logger.info("Sent InsuranceQuoteExpiredEvent for insurance quote request {} to the Customer Self-Service backend.", insuranceQuoteRequest.getId());
			} else {
				logger.info("The insurance quote for request {} has been accepted", insuranceQuoteRequest.getId());
				insuranceQuoteRequest.acceptQuote(decisionDate);
				logger.info("Creating policy for insurance quote request '{}'.", insuranceQuoteRequest.getId());
				PolicyAggregateRoot policy = createPolicyForInsuranceQuoteRequest(insuranceQuoteRequest);
				String policyId = policy.getId().getId();
				logger.info("Saving policy with id '{}' for insurance quote request with id '{}'", policyId, insuranceQuoteRequest.getId());
				policyRepository.save(policy);
				logger.info("Policy saved with id '{}'.", policyId);
				Date policyCreationDate = new Date();
				logger.info("Finalizing quote for insurance quote request '{}'.", insuranceQuoteRequest.getId());
				insuranceQuoteRequest.finalizeQuote(policyId, policyCreationDate);

				PolicyCreatedEvent policyCreatedEvent = new PolicyCreatedEvent(policyCreationDate, insuranceQuoteRequest.getId(), policyId);
				logger.info("Sending PolicyCreatedEvent for insurance quote request {} to the Customer Self-Service backend.", insuranceQuoteRequest.getId());
				customerSelfServiceMessageProducer.sendPolicyCreatedEvent(policyCreatedEvent);
				logger.info("Sent PolicyCreatedEvent for insurance quote request {} to the Customer Self-Service backend.", insuranceQuoteRequest.getId());

				CustomerInfoEntity customerInfo = insuranceQuoteRequest.getCustomerInfo();
				logger.info("Fetching customer info for customerId '{}'.", customerInfo.getCustomerId());
				List<CustomerDto> customers = customerCoreRemoteProxy.getCustomersById(customerInfo.getCustomerId());
				logger.info("CustomerCoreRemoteProxy returned {} customers.", customers.size());
				if(!customers.isEmpty()) {
					CustomerDto customer = customers.get(0);
					logger.info("Using customer: {}", customer);
					final PolicyDto policyDto = PolicyDto.fromDomainObject(policy);
					final UpdatePolicyEvent event = new UpdatePolicyEvent("<customer-self-service-backend>", decisionDate, customer, policyDto);
					logger.info("Sending UpdatePolicyEvent for policy {} to Risk Management backend.", policy.getId().getId());
					riskManagementMessageProducer.emitEvent(event);
					logger.info("Sent UpdatePolicyEvent for policy {} to Risk Management backend.", policy.getId().getId());
				} else {
					logger.warn("No customer found for customerId '{}'.", customerInfo.getCustomerId());
				}
			}
		} else {
			/*
			 * If a quote has been rejected by the customer after it has already expired,
			 * we discard the QUOTE_EXPIRED status in favor of the QUOTE_REJECTED status.
			 * */
			logger.info("Customer rejected the quote for request '{}'.", id);
			if(insuranceQuoteRequest.getStatus().equals(RequestStatus.QUOTE_EXPIRED)) {
				logger.info("Status is QUOTE_EXPIRED, popping status for request '{}'.", insuranceQuoteRequest.getId());
				insuranceQuoteRequest.popStatus();
			}

			insuranceQuoteRequest.rejectQuote(decisionDate);
		}

		logger.info("Saving Insurance Quote Request with id '{}' to the repository", id);
		insuranceQuoteRequestRepository.save(insuranceQuoteRequest);
		logger.info("Insurance Quote Request with id '{}' saved.", id);
		logger.info("Method receiveCustomerDecision ended.");
	}

	private PolicyAggregateRoot createPolicyForInsuranceQuoteRequest(InsuranceQuoteRequestAggregateRoot insuranceQuoteRequest) {
		logger.info("Method createPolicyForInsuranceQuoteRequest started.");
		PolicyId policyId = PolicyId.random();
		logger.info("Generated random PolicyId: {}", policyId);
		CustomerId customerId = insuranceQuoteRequest.getCustomerInfo().getCustomerId();
		logger.info("CustomerId: {}", customerId);

		Date startDate = insuranceQuoteRequest.getInsuranceOptions().getStartDate();
		logger.info("Policy start date: {}", startDate);
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(startDate);
		calendar.add(Calendar.YEAR, 1);
		Date endDate = calendar.getTime();
		logger.info("Policy end date: {}", endDate);
		PolicyPeriod policyPeriod = new PolicyPeriod(startDate, endDate);

		PolicyType policyType = new PolicyType(insuranceQuoteRequest.getInsuranceOptions().getInsuranceType().getName());
		logger.info("PolicyType: {}", policyType);
		MoneyAmount deductible = insuranceQuoteRequest.getInsuranceOptions().getDeductible();
		MoneyAmount insurancePremium = insuranceQuoteRequest.getInsuranceQuote().getInsurancePremium();
		MoneyAmount policyLimit = insuranceQuoteRequest.getInsuranceQuote().getPolicyLimit();
		logger.info("Deductible: {}, InsurancePremium: {}, PolicyLimit: {}", deductible, insurancePremium, policyLimit);
		InsuringAgreementEntity insuringAgreement = new InsuringAgreementEntity(Collections.emptyList());
		logger.info("Creating PolicyAggregateRoot object.");
		PolicyAggregateRoot policy = new PolicyAggregateRoot(policyId, customerId, new Date(), policyPeriod, policyType, deductible, policyLimit, insurancePremium, insuringAgreement);
		logger.info("Method createPolicyForInsuranceQuoteRequest ended.");
		return policy;
	}
}

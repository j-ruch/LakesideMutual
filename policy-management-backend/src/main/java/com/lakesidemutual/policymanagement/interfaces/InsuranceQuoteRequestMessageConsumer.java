package com.lakesidemutual.policymanagement.interfaces;


import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import com.lakesidemutual.policymanagement.domain.insurancequoterequest.CustomerInfoEntity;
import com.lakesidemutual.policymanagement.domain.insurancequoterequest.InsuranceOptionsEntity;
import com.lakesidemutual.policymanagement.domain.insurancequoterequest.InsuranceQuoteRequestAggregateRoot;
import com.lakesidemutual.policymanagement.domain.insurancequoterequest.InsuranceQuoteRequestEvent;
import com.lakesidemutual.policymanagement.domain.insurancequoterequest.RequestStatus;
import com.lakesidemutual.policymanagement.infrastructure.InsuranceQuoteRequestRepository;
import com.lakesidemutual.policymanagement.interfaces.dtos.insurancequoterequest.InsuranceQuoteRequestDto;
import com.lakesidemutual.policymanagement.interfaces.dtos.insurancequoterequest.RequestStatusChangeDto;

/**
 * InsuranceQuoteRequestMessageConsumer is a Spring component that consumes InsuranceQuoteRequestEvents
 * as they arrive through the ActiveMQ message queue. It processes these events by creating corresponding
 * InsuranceQuoteRequestAggregateRoot instances.
 * */
@Component
public class InsuranceQuoteRequestMessageConsumer {
	private final Logger logger = LoggerFactory.getLogger(this.getClass());

	@Autowired
	private InsuranceQuoteRequestRepository insuranceQuoteRequestRepository;

	@JmsListener(destination = "${insuranceQuoteRequestEvent.queueName}")
	public void receiveInsuranceQuoteRequest(final Message<InsuranceQuoteRequestEvent> message) {
		logger.info("Method receiveInsuranceQuoteRequest started.");
		logger.info("Raw JMS message: {}", message);

		InsuranceQuoteRequestEvent insuranceQuoteRequestEvent = message.getPayload();
		logger.info("InsuranceQuoteRequestEvent payload: {}", insuranceQuoteRequestEvent);
		InsuranceQuoteRequestDto insuranceQuoteRequestDto = insuranceQuoteRequestEvent.getInsuranceQuoteRequestDto();
		logger.info("InsuranceQuoteRequestDto: {}", insuranceQuoteRequestDto);
		Long id = insuranceQuoteRequestDto.getId();
		Date date = insuranceQuoteRequestDto.getDate();
		logger.info("InsuranceQuoteRequestDto id: {}, date: {}", id, date);
		List<RequestStatusChangeDto> statusHistory = insuranceQuoteRequestDto.getStatusHistory();
		logger.info("Status history: {}", statusHistory);
		RequestStatus status = RequestStatus.valueOf(statusHistory.get(statusHistory.size()-1).getStatus());
		logger.info("Determined status: {}", status);

		CustomerInfoEntity customerInfo = insuranceQuoteRequestDto.getCustomerInfo().toDomainObject();
		logger.info("CustomerInfoEntity: {}", customerInfo);
		InsuranceOptionsEntity insuranceOptions = insuranceQuoteRequestDto.getInsuranceOptions().toDomainObject();
		logger.info("InsuranceOptionsEntity: {}", insuranceOptions);

		InsuranceQuoteRequestAggregateRoot insuranceQuoteAggregateRoot = new InsuranceQuoteRequestAggregateRoot(id, date, status, customerInfo, insuranceOptions, null, null);
		logger.info("Created InsuranceQuoteRequestAggregateRoot: {}", insuranceQuoteAggregateRoot);
		logger.info("Saving new Insurance Quote Request with id '{}' and status '{}'", insuranceQuoteAggregateRoot.getId(), insuranceQuoteAggregateRoot.getStatus());
		insuranceQuoteRequestRepository.save(insuranceQuoteAggregateRoot);
		logger.info("Insurance Quote Request with id '{}' saved.", insuranceQuoteAggregateRoot.getId());
		logger.info("Method receiveInsuranceQuoteRequest ended.");
	}
}

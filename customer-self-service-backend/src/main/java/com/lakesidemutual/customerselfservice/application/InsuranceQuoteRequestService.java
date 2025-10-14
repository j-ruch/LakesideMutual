package com.lakesidemutual.customerselfservice.application;

import com.lakesidemutual.customerselfservice.domain.customer.Address;
import com.lakesidemutual.customerselfservice.domain.customer.CustomerId;
import com.lakesidemutual.customerselfservice.domain.insurancequoterequest.*;
import org.bouncycastle.pqc.legacy.math.linearalgebra.BigEndianConversions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.Query;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.*;

@Service
public class InsuranceQuoteRequestService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public List<InsuranceQuoteRequestAggregateRoot> findByCustomerInfo_CustomerIdOrderByDateDesc(CustomerId customerId) {
        String getInsuranceQuoteRequestsById = "SELECT * FROM insurancequoterequests " +
                "LEFT JOIN customerinfos ON insurancequoterequests.customer_info_id = customerinfos.id " +
                "LEFT JOIN addresses contact_address ON customerinfos.contact_address_id = contact_address.id " +
                "LEFT JOIN addresses billing_address ON customerinfos.billing_address_id = billing_address.id " +
                "LEFT JOIN insuranceoptions ON insurancequoterequests.insurance_options_id = insuranceoptions.id " +
                "LEFT JOIN insurancequotes ON insurancequoterequests.insurance_quote_id = insurancequotes.id " +
                "LEFT JOIN insurancequoterequests_status_history ON insurancequoterequests.id = insurancequoterequests_status_history.insurance_quote_request_aggregate_root_id " +
                "LEFT JOIN requeststatuschanges ON insurancequoterequests_status_history.status_history_id = requeststatuschanges.id " +
                "WHERE customerinfos.customer_id = ? " +
                "ORDER BY insurancequoterequests.date DESC";

        Map<Long, InsuranceQuoteRequestAggregateRoot> insuranceQuoteRequestAggregateRootMap = new HashMap<>();

        jdbcTemplate.query(getInsuranceQuoteRequestsById, resultSet -> {
            Long insuranceQuoteRequestAggregateRootId = resultSet.getLong("insurance_quote_request_aggregate_root_id");

            InsuranceQuoteRequestAggregateRoot insuranceQuoteRequestAggregateRoot = null;
            if (!insuranceQuoteRequestAggregateRootMap.containsKey(insuranceQuoteRequestAggregateRootId)) {
                Date date = resultSet.getDate("date");
                Long customerInfoId = resultSet.getLong("customer_info_id");
                Long insuranceOptionsId = resultSet.getLong("insurance_options_id");
                Long insuranceQuoteId = resultSet.getObject("insurance_quote_id") != null ? resultSet.getLong("insurance_quote_id") : null;
                String policyId = resultSet.getString("policy_id");
                Long contactAddressId = resultSet.getLong("contact_address_id");
                Long billingAddressId = resultSet.getLong("billing_address_id");
                String firstName = resultSet.getString("firstname");
                String lastName = resultSet.getString("lastname");

                String contactAddressCity = resultSet.getString("contact_address.city");
                String contactAddressStreet = resultSet.getString("contact_address.street_address");
                String contactAddressPostalCode = resultSet.getString("contact_address.postal_code");
                Address contactAddress = new Address(contactAddressId, contactAddressStreet, contactAddressPostalCode, contactAddressCity);

                String billingAddressCity = resultSet.getString("billing_address.city");
                String billingAddressStreet = resultSet.getString("billing_address.street_address");
                String billingAddressPostalCode = resultSet.getString("billing_address.postal_code");
                Address billingAddress = new Address(billingAddressId, billingAddressStreet, billingAddressPostalCode, billingAddressCity);

                CustomerInfoEntity customerInfo = new CustomerInfoEntity(
                        customerInfoId,
                        customerId,
                        firstName,
                        lastName,
                        contactAddress,
                        billingAddress
                );

                BigDecimal deductibleAmount = resultSet.getBigDecimal("deductible_amount");
                Currency deductibleCurrency = Currency.getInstance(resultSet.getString("deductible_currency"));
                MoneyAmount deductible = new MoneyAmount(deductibleAmount, deductibleCurrency);
                Date startDate = resultSet.getDate("start_date");
                InsuranceType insuranceType = new InsuranceType(resultSet.getString("name"));
                InsuranceOptionsEntity insuranceOptions = new InsuranceOptionsEntity(
                        insuranceOptionsId,
                        startDate,
                        insuranceType,
                        deductible
                );

                InsuranceQuoteEntity insuranceQuote = null;
                if (insuranceQuoteId != null) {
                    BigDecimal insurancePremiumAmount = resultSet.getBigDecimal("insurance_premium_amount");
                    Currency insurancePremiumCurrency = Currency.getInstance(resultSet.getString("insurance_premium_currency"));
                    MoneyAmount insurancePremium = new MoneyAmount(insurancePremiumAmount, insurancePremiumCurrency);
                    BigDecimal policyLimitAmount = resultSet.getBigDecimal("policy_limit_amount");
                    Currency policyLimitCurrency = Currency.getInstance(resultSet.getString("policy_limit_currency"));
                    MoneyAmount policyLimit = new MoneyAmount(policyLimitAmount, policyLimitCurrency);
                    Date expirationDate = resultSet.getDate("expiration_date");

                    insuranceQuote = new InsuranceQuoteEntity(
                            insuranceQuoteId,
                            expirationDate,
                            insurancePremium,
                            policyLimit
                    );
                }

                insuranceQuoteRequestAggregateRoot = new InsuranceQuoteRequestAggregateRoot(
                        insuranceQuoteRequestAggregateRootId,
                        date,
                        new ArrayList<>(),
                        customerInfo,
                        insuranceOptions,
                        insuranceQuote,
                        policyId
                );
                insuranceQuoteRequestAggregateRootMap.put(insuranceQuoteRequestAggregateRootId, insuranceQuoteRequestAggregateRoot);
            }

            if (insuranceQuoteRequestAggregateRoot != null) {
                Long statusHistoryId = resultSet.getLong("status_history_id");
                Date statusChangeDate = resultSet.getDate("requeststatuschanges.date");
                RequestStatus status = RequestStatus.valueOf(resultSet.getString("status"));
                RequestStatusChange statusChange = new RequestStatusChange(statusHistoryId, statusChangeDate, status);
                insuranceQuoteRequestAggregateRoot.getStatusHistory().add(statusChange);
            }
        }, customerId.getId());

        return new ArrayList<>(insuranceQuoteRequestAggregateRootMap.values());
    }
}

package com.lakesidemutual.customercore.infrastructure;

import com.google.protobuf.Timestamp;
import com.lakesidemutual.customercore.application.CustomerService;
import com.lakesidemutual.customercore.domain.customer.CustomerAggregateRoot;
import com.lakesidemutual.customercore.grpc.*;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@GrpcService
public class CustomerCoreRemoteGrpcService extends CustomerCoreProtoServiceGrpc.CustomerCoreProtoServiceImplBase {

    @Autowired
    private CustomerService customerService;

    @Transactional(readOnly = true)
    @Override
    public void getCustomerById(CustomerCoreProtoRequestDto request, StreamObserver<CustomerCoreProtoResponseDto> responseObserver) {
        List<CustomerAggregateRoot> customers = customerService.getCustomers(request.getCustomerId());

        customers.stream()
                .map(customer ->
                        CustomerCoreProtoResponseDto.newBuilder()
                                .setCustomerId(customer.getId().getId())
                                .setCustomerProfile(CustomerProfileProtoDto.newBuilder()
                                        .setFirstname(customer.getCustomerProfile().getFirstname())
                                        .setLastname(customer.getCustomerProfile().getLastname())
                                        .setBirthday(Timestamp.newBuilder()
                                                .setSeconds(customer.getCustomerProfile().getBirthday().toInstant().getEpochSecond())
                                                .setNanos(customer.getCustomerProfile().getBirthday().toInstant().getNano())
                                                .build())
                                        .setAddress(AddressProtoDto.newBuilder()
                                                .setStreetAddress(customer.getCustomerProfile().getCurrentAddress().getStreetAddress())
                                                .setPostalCode(customer.getCustomerProfile().getCurrentAddress().getPostalCode())
                                                .setCity(customer.getCustomerProfile().getCurrentAddress().getCity())
                                                .build())
                                        .setEmail(customer.getCustomerProfile().getEmail())
                                        .setPhoneNumber(customer.getCustomerProfile().getPhoneNumber())
                                        .addAllMoveHistory(
                                                customer.getCustomerProfile().getMoveHistory().stream()
                                                        .map(address -> AddressProtoDto.newBuilder()
                                                                .setStreetAddress(address.getStreetAddress())
                                                                .setPostalCode(address.getPostalCode())
                                                                .setCity(address.getCity())
                                                                .build())
                                                        .toList())
                                        .build())
                                .build())
                        .forEach(responseObserver::onNext);

        responseObserver.onCompleted();
    }
}

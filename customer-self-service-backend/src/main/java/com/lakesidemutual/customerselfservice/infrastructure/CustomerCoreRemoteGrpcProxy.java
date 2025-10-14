package com.lakesidemutual.customerselfservice.infrastructure;

import com.lakesidemutual.customercore.grpc.CustomerCoreProtoRequestDto;
import com.lakesidemutual.customercore.grpc.CustomerCoreProtoResponseDto;
import com.lakesidemutual.customercore.grpc.CustomerCoreProtoServiceGrpc;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

@Service
public class CustomerCoreRemoteGrpcProxy {

    @GrpcClient("customerCoreGrpcProxy")
    private CustomerCoreProtoServiceGrpc.CustomerCoreProtoServiceBlockingStub blockingStub;

    public CustomerCoreProtoResponseDto getCustomerById(String customerId) {
        CustomerCoreProtoRequestDto requestDto = CustomerCoreProtoRequestDto.newBuilder()
                .setCustomerId(customerId)
                .build();
        return blockingStub.getCustomerById(requestDto);
    }
}

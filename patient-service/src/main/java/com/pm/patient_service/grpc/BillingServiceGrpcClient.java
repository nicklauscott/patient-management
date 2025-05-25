package com.pm.patient_service.grpc;

import billing.BillingRequest;
import billing.BillingResponse;
import billing.BillingServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class BillingServiceGrpcClient {

    private static final Logger log = LoggerFactory.getLogger(BillingServiceGrpcClient.class);
    // We will use 'BillingServiceBlockingStub' to make a gRPC synchronous request
    private final BillingServiceGrpc.BillingServiceBlockingStub blockingStub;

    /** We inject the grpc server address from environment variables
       'billing-service' refers to the Billing service container in docker
       '9001' refers to the Billing service container port in docker
     */
    public BillingServiceGrpcClient(
            @Value("${billing.service.address:localhost}") String serverAddress,
            @Value("${billing.service.grpc.port:9001}") String serverPort
    ) {
        log.info("Connecting to GRPC Billing service at {}:{}", serverAddress, serverPort);
        // We use the serverAddress and serverPort to create a managed channel
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(serverAddress, Integer.parseInt(serverPort))
                .usePlaintext() // Disables encryption for local testing
                .build();
        this.blockingStub = BillingServiceGrpc.newBlockingStub(channel);
    }

    public BillingResponse createBillingAccount(
            String patientId, String name, String email
    ) {
        // We create a patient request
        BillingRequest request = BillingRequest.newBuilder()
                .setPatientId(patientId).setName(name).setEmail(email).build();

        // We send the request and return the response
        BillingResponse response = blockingStub.createBillingAccount(request);
        log.info("Received GRPC billing response: {}", response.toString());
        return response;
    }
}


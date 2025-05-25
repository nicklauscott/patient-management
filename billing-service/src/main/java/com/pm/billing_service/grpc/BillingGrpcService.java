package com.pm.billing_service.grpc;

import billing.BillingResponse;
import billing.BillingServiceGrpc;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** This tells Spring that this is a Grpc Service.
   We also extend the BillingServiceImplBase class that was generated */
@GrpcService
public class BillingGrpcService extends BillingServiceGrpc.BillingServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(BillingGrpcService.class);

    /** We override the 'createBillingAccount' from the generated class;
        this is also the method we created in the billing_server.proto file.
        We use the StreamObserver to send multiple responses to the client and
        also accept back-and-forth communication the same client. */
    @Override
    public void createBillingAccount(
        billing.BillingRequest billingRequest,
        StreamObserver<billing.BillingResponse> responseObserver
    ) {
        log.info("createBillingAccount request received {}", billingRequest.toString());

        // save data to the database

        // We create the BillingResponse
        BillingResponse response = BillingResponse.newBuilder()
                .setAccountId("12345").setStatus("ACTIVE").build();

        // We then respond with the BillingResponse
        responseObserver.onNext(response);

        // We close the response stream
        responseObserver.onCompleted();
    }

}


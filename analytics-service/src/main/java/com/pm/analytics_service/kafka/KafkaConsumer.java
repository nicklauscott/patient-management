package com.pm.analytics_service.kafka;

import com.google.protobuf.InvalidProtocolBufferException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import patient.events.PatientEvent;

/** With the '@Service' annotation, our 'KafkaConsumer' will start up when our
 * application does and our 'consumeEvent' method will start consuming event
 * with the help of the '@KafkaListener' annotation. */
@Service
public class KafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumer.class);

    /** 1. We use the '@KafkaListener' to specific the type (i.e., topics) of event
           we want to listen to, and the 'groupId' tells kafka who this consumer is.
        2. We'll be receiving the event as a byte array so we have to convert to the
           PatientEvent object. */
    @KafkaListener(topics = "patient", groupId = "analytics-service")
    public void consumeEvent(byte[] event) {
        try {
            PatientEvent patientEvent = PatientEvent.parseFrom(event);
            // ... perform analytics logic here
            log.info(
               "Received patient even: [PatientId={}, PatientName={}, PatientEmail{}]",
               patientEvent.getPatientId(), patientEvent.getName(), patientEvent.getEmail()
            );
        } catch (InvalidProtocolBufferException e) {
            log.error("Error deserializing event {}", e.getMessage());
        }
    }
}



package com.suraj.candidates.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class CandidateEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(CandidateEventPublisher.class);

    private final KafkaTemplate<String, CandidateEvent> kafkaTemplate;
    private final String topicName;

    public CandidateEventPublisher(
            KafkaTemplate<String, CandidateEvent> kafkaTemplate,
            @Value("${app.kafka.topic}") String topicName) {
        this.kafkaTemplate = kafkaTemplate;
        this.topicName = topicName;
    }

    public void publish(CandidateEvent event) {
        String key = String.valueOf(event.candidateId());
        try {
            var result = kafkaTemplate.send(topicName, key, event).get(5, TimeUnit.SECONDS);
            var metadata = result.getRecordMetadata();
            log.info("Published {} for candidateId={} to topic={} partition={} offset={}",
                    event.eventType(), event.candidateId(), metadata.topic(), metadata.partition(), metadata.offset());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing candidate event " + event.eventId(), e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to publish candidate event " + event.eventId(), e);
        }
    }
}

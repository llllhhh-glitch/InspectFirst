package com.inspectfirst.report;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.backoff.FixedBackOff;

@Component
@ConditionalOnProperty(name="inspectfirst.events-enabled",havingValue="true",matchIfMissing=true)
public class KafkaEvents {
    private final ReportEvents events;
    public KafkaEvents(ReportEvents events) { this.events=events; }
    @KafkaListener(topics="inspectfirst.inspection.events.v1") public void receive(String message) { events.accept(message); }
    @Bean DefaultErrorHandler retryUntilRecovered() { return new DefaultErrorHandler(new FixedBackOff(2000L,FixedBackOff.UNLIMITED_ATTEMPTS)); }
}

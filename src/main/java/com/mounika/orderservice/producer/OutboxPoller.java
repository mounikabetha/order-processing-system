package com.mounika.orderservice.producer;

import com.mounika.orderservice.entity.OutboxEvent;
import com.mounika.orderservice.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPoller {

    private static final String TOPIC = "order.events";

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void pollAndPublish() {
        List<OutboxEvent> events = outboxEventRepository
                .findTop50ByPublishedFalseOrderByCreatedAtAsc();

        for (OutboxEvent event : events) {
            try {
                kafkaTemplate.send(TOPIC, event.getAggregateId(), event.getPayload())
                        .whenComplete((result, ex) -> {
                            if (ex != null) {
                                log.error("Failed to publish event: eventId={}", event.getId(), ex);
                            }
                        });

                event.setPublished(true);
                event.setPublishedAt(Instant.now());
                outboxEventRepository.save(event);

                log.debug("Published outbox event: eventId={}, type={}",
                        event.getId(), event.getEventType());
            } catch (Exception e) {
                log.error("Error publishing outbox event: eventId={}", event.getId(), e);
                break; // Preserve ordering — stop on first failure
            }
        }
    }
}

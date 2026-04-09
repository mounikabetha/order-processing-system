package com.mounika.orderservice.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mounika.orderservice.entity.ProcessedEvent;
import com.mounika.orderservice.enums.EventType;
import com.mounika.orderservice.event.OrderEvent;
import com.mounika.orderservice.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationConsumer {

    private static final String CONSUMER_GROUP = "notification-service";

    private static final Map<EventType, String> NOTIFICATION_TEMPLATES = Map.of(
            EventType.ORDER_CREATED, "Your order #%s has been placed! Total: $%s",
            EventType.ORDER_PAID, "Payment confirmed for order #%s. We're preparing your items!",
            EventType.ORDER_SHIPPED, "Great news! Order #%s has been shipped!",
            EventType.ORDER_DELIVERED, "Order #%s has been delivered. Enjoy!",
            EventType.ORDER_CANCELLED, "Order #%s has been cancelled. Refund will be processed shortly.",
            EventType.ORDER_PAYMENT_FAILED, "Payment failed for order #%s. Please update your payment method.",
            EventType.ORDER_REFUNDED, "Refund processed for order #%s. Amount: $%s"
    );

    private static final Set<EventType> NOTIFIABLE_EVENTS = NOTIFICATION_TEMPLATES.keySet();

    private final ObjectMapper objectMapper;
    private final ProcessedEventRepository processedEventRepository;

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            dltTopicSuffix = ".notification-dlq"
    )
    @KafkaListener(topics = "order.events", groupId = CONSUMER_GROUP)
    @Transactional
    public void handleOrderEvent(ConsumerRecord<String, String> record) {
        try {
            OrderEvent event = objectMapper.readValue(record.value(), OrderEvent.class);

            // Idempotency check
            if (processedEventRepository.existsByEventIdAndConsumerGroup(
                    event.getEventId(), CONSUMER_GROUP)) {
                log.debug("Duplicate notification event, skipping: eventId={}", event.getEventId());
                return;
            }

            if (NOTIFIABLE_EVENTS.contains(event.getEventType())) {
                sendNotification(event);
            }

            // Mark as processed
            processedEventRepository.save(ProcessedEvent.builder()
                    .eventId(event.getEventId())
                    .consumerGroup(CONSUMER_GROUP)
                    .build());

        } catch (Exception e) {
            log.error("Failed to process notification event: key={}", record.key(), e);
            throw new RuntimeException("Notification processing failed", e);
        }
    }

    private void sendNotification(OrderEvent event) {
        String template = NOTIFICATION_TEMPLATES.get(event.getEventType());
        String shortOrderId = event.getOrderId().substring(0, 8);
        String message = String.format(template, shortOrderId, event.getTotalAmount());

        // Simulate sending email
        log.info("EMAIL → customer={} | subject='Order Update' | body='{}'",
                event.getCustomerId(), message);

        // Simulate sending SMS
        log.info("SMS → customer={} | message='{}'",
                event.getCustomerId(), message);

        // In production: call SendGrid/SES for email, Twilio for SMS
    }
}

package com.mounika.orderservice.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mounika.orderservice.entity.ProcessedEvent;
import com.mounika.orderservice.enums.EventType;
import com.mounika.orderservice.enums.OrderStatus;
import com.mounika.orderservice.event.OrderEvent;
import com.mounika.orderservice.repository.ProcessedEventRepository;
import com.mounika.orderservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentConsumer {

    private static final String CONSUMER_GROUP = "payment-service";

    private final ObjectMapper objectMapper;
    private final OrderService orderService;
    private final ProcessedEventRepository processedEventRepository;

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            dltTopicSuffix = ".dlq"
    )
    @KafkaListener(topics = "order.events", groupId = CONSUMER_GROUP)
    @Transactional
    public void handleOrderEvent(ConsumerRecord<String, String> record) {
        try {
            OrderEvent event = objectMapper.readValue(record.value(), OrderEvent.class);

            // Idempotency check
            if (processedEventRepository.existsByEventIdAndConsumerGroup(
                    event.getEventId(), CONSUMER_GROUP)) {
                log.info("Duplicate event detected, skipping: eventId={}", event.getEventId());
                return;
            }

            if (event.getEventType() == EventType.ORDER_CREATED) {
                processPayment(event);
            }

            // Record as processed
            processedEventRepository.save(ProcessedEvent.builder()
                    .eventId(event.getEventId())
                    .consumerGroup(CONSUMER_GROUP)
                    .build());

        } catch (Exception e) {
            log.error("Failed to process order event: key={}", record.key(), e);
            throw new RuntimeException("Event processing failed", e);
        }
    }

    private void processPayment(OrderEvent event) {
        log.info("Processing payment for order: orderId={}, amount={}",
                event.getOrderId(), event.getTotalAmount());

        // Simulate payment processing
        // In production, this would call a payment gateway
        orderService.transitionOrder(
                java.util.UUID.fromString(event.getOrderId()),
                OrderStatus.PAID,
                EventType.ORDER_PAID
        );

        log.info("Payment successful for order: orderId={}", event.getOrderId());
    }

    @KafkaListener(topics = "order.events.dlq", groupId = CONSUMER_GROUP + "-dlq")
    public void handleDlq(ConsumerRecord<String, String> record) {
        log.error("DLQ received — manual intervention required: key={}, value={}",
                record.key(), record.value());
        // In production: alert ops, write to incident table, etc.
    }
}

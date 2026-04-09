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

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class ShippingConsumer {

    private static final String CONSUMER_GROUP = "shipping-service";

    private final ObjectMapper objectMapper;
    private final OrderService orderService;
    private final ProcessedEventRepository processedEventRepository;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            dltTopicSuffix = ".shipping-dlq"
    )
    @KafkaListener(topics = "order.events", groupId = CONSUMER_GROUP)
    @Transactional
    public void handleOrderEvent(ConsumerRecord<String, String> record) {
        try {
            OrderEvent event = objectMapper.readValue(record.value(), OrderEvent.class);

            // Idempotency check
            if (processedEventRepository.existsByEventIdAndConsumerGroup(
                    event.getEventId(), CONSUMER_GROUP)) {
                log.debug("Duplicate shipping event, skipping: eventId={}", event.getEventId());
                return;
            }

            if (event.getEventType() == EventType.ORDER_PAID) {
                processShipment(event);
            }

            processedEventRepository.save(ProcessedEvent.builder()
                    .eventId(event.getEventId())
                    .consumerGroup(CONSUMER_GROUP)
                    .build());

        } catch (Exception e) {
            log.error("Failed to process shipping event: key={}", record.key(), e);
            throw new RuntimeException("Shipping processing failed", e);
        }
    }

    private void processShipment(OrderEvent event) {
        UUID orderId = UUID.fromString(event.getOrderId());
        String trackingNumber = "TRK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        log.info("SHIPPING → Preparing shipment for order={}, tracking={}",
                event.getOrderId(), trackingNumber);

        // Transition to SHIPPED
        orderService.transitionOrder(orderId, OrderStatus.SHIPPED, EventType.ORDER_SHIPPED);
        log.info("SHIPPING → Order {} shipped with tracking {}", event.getOrderId(), trackingNumber);

        // Simulate delivery after 10 seconds (in production, this would be a webhook from carrier)
        scheduler.schedule(() -> {
            try {
                orderService.transitionOrder(orderId, OrderStatus.DELIVERED, EventType.ORDER_DELIVERED);
                log.info("DELIVERY → Order {} delivered!", event.getOrderId());
            } catch (Exception e) {
                log.error("Failed to mark order as delivered: orderId={}", orderId, e);
            }
        }, 10, TimeUnit.SECONDS);
    }
}

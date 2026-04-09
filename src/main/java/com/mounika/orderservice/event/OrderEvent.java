package com.mounika.orderservice.event;

import com.mounika.orderservice.enums.EventType;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderEvent {

    private UUID eventId;
    private String orderId;
    private String customerId;
    private EventType eventType;
    private BigDecimal totalAmount;
    private String previousStatus;
    private String currentStatus;
    private Instant timestamp;
}

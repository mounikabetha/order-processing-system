package com.mounika.orderservice.enums;

import java.util.Set;
import java.util.Map;

public enum OrderStatus {
    CREATED,
    PAYMENT_PENDING,
    PAID,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    PAYMENT_FAILED,
    REFUNDED;

    private static final Map<OrderStatus, Set<OrderStatus>> VALID_TRANSITIONS = Map.of(
            CREATED, Set.of(PAYMENT_PENDING, CANCELLED),
            PAYMENT_PENDING, Set.of(PAID, PAYMENT_FAILED, CANCELLED),
            PAID, Set.of(SHIPPED, REFUNDED),
            SHIPPED, Set.of(DELIVERED),
            PAYMENT_FAILED, Set.of(PAYMENT_PENDING, CANCELLED)
    );

    public boolean canTransitionTo(OrderStatus target) {
        return VALID_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }
}

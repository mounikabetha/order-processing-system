package com.mounika.orderservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mounika.orderservice.dto.OrderDto;
import com.mounika.orderservice.entity.*;
import com.mounika.orderservice.enums.EventType;
import com.mounika.orderservice.enums.OrderStatus;
import com.mounika.orderservice.event.OrderEvent;
import com.mounika.orderservice.exception.OrderNotFoundException;
import com.mounika.orderservice.repository.OrderRepository;
import com.mounika.orderservice.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public OrderDto.Response createOrder(OrderDto.CreateRequest request) {
        Order order = Order.builder()
                .customerId(request.getCustomerId())
                .status(OrderStatus.CREATED)
                .totalAmount(BigDecimal.ZERO)
                .shippingAddress(ShippingAddress.builder()
                        .street(request.getShippingAddress().getStreet())
                        .city(request.getShippingAddress().getCity())
                        .state(request.getShippingAddress().getState())
                        .zipCode(request.getShippingAddress().getZipCode())
                        .build())
                .build();

        BigDecimal total = BigDecimal.ZERO;
        for (OrderDto.ItemRequest itemReq : request.getItems()) {
            OrderItem item = OrderItem.builder()
                    .productId(itemReq.getProductId())
                    .quantity(itemReq.getQuantity())
                    .price(itemReq.getPrice())
                    .build();
            order.addItem(item);
            total = total.add(item.getSubtotal());
        }
        order.setTotalAmount(total);

        Order saved = orderRepository.save(order);
        saveOutboxEvent(saved, EventType.ORDER_CREATED, null, OrderStatus.CREATED);

        log.info("Order created: orderId={}, customerId={}, total={}",
                saved.getId(), saved.getCustomerId(), saved.getTotalAmount());

        return mapToResponse(saved);
    }

    @Cacheable(value = "orders", key = "#orderId")
    @Transactional(readOnly = true)
    public OrderDto.Response getOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        return mapToResponse(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderDto.Response> getOrdersByCustomer(String customerId, Pageable pageable) {
        return orderRepository.findByCustomerId(customerId, pageable)
                .map(this::mapToResponse);
    }

    @CacheEvict(value = "orders", key = "#orderId")
    @Transactional
    public OrderDto.Response cancelOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        OrderStatus previous = order.getStatus();
        order.transitionTo(OrderStatus.CANCELLED);
        Order saved = orderRepository.save(order);

        saveOutboxEvent(saved, EventType.ORDER_CANCELLED, previous, OrderStatus.CANCELLED);
        log.info("Order cancelled: orderId={}", orderId);

        return mapToResponse(saved);
    }

    @CacheEvict(value = "orders", key = "#orderId")
    @Transactional
    public OrderDto.Response transitionOrder(UUID orderId, OrderStatus newStatus, EventType eventType) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        OrderStatus previous = order.getStatus();
        order.transitionTo(newStatus);
        Order saved = orderRepository.save(order);

        saveOutboxEvent(saved, eventType, previous, newStatus);
        log.info("Order transitioned: orderId={}, {} -> {}", orderId, previous, newStatus);

        return mapToResponse(saved);
    }

    private void saveOutboxEvent(Order order, EventType eventType,
                                  OrderStatus previousStatus, OrderStatus currentStatus) {
        OrderEvent event = OrderEvent.builder()
                .eventId(UUID.randomUUID())
                .orderId(order.getId().toString())
                .customerId(order.getCustomerId())
                .eventType(eventType)
                .totalAmount(order.getTotalAmount())
                .previousStatus(previousStatus != null ? previousStatus.name() : null)
                .currentStatus(currentStatus.name())
                .timestamp(Instant.now())
                .build();

        try {
            OutboxEvent outbox = OutboxEvent.builder()
                    .aggregateId(order.getId().toString())
                    .eventType(eventType)
                    .payload(objectMapper.writeValueAsString(event))
                    .build();
            outboxEventRepository.save(outbox);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize order event", e);
        }
    }

    private OrderDto.Response mapToResponse(Order order) {
        return OrderDto.Response.builder()
                .id(order.getId())
                .customerId(order.getCustomerId())
                .status(order.getStatus().name())
                .totalAmount(order.getTotalAmount())
                .items(order.getItems().stream().map(item ->
                        OrderDto.ItemResponse.builder()
                                .id(item.getId())
                                .productId(item.getProductId())
                                .quantity(item.getQuantity())
                                .price(item.getPrice())
                                .subtotal(item.getSubtotal())
                                .build()
                ).toList())
                .shippingAddress(OrderDto.AddressResponse.builder()
                        .street(order.getShippingAddress().getStreet())
                        .city(order.getShippingAddress().getCity())
                        .state(order.getShippingAddress().getState())
                        .zipCode(order.getShippingAddress().getZipCode())
                        .build())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}

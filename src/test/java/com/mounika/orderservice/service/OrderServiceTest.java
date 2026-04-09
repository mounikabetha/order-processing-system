package com.mounika.orderservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mounika.orderservice.dto.OrderDto;
import com.mounika.orderservice.entity.Order;
import com.mounika.orderservice.entity.OrderItem;
import com.mounika.orderservice.entity.ShippingAddress;
import com.mounika.orderservice.enums.OrderStatus;
import com.mounika.orderservice.exception.OrderNotFoundException;
import com.mounika.orderservice.repository.OrderRepository;
import com.mounika.orderservice.repository.OutboxEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Spy  private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private OrderService orderService;

    @Nested
    @DisplayName("createOrder")
    class CreateOrder {

        @Test
        @DisplayName("should create order with correct total and status CREATED")
        void shouldCreateOrder() {
            // Given
            OrderDto.CreateRequest request = OrderDto.CreateRequest.builder()
                    .customerId("cust-123")
                    .items(List.of(
                            OrderDto.ItemRequest.builder()
                                    .productId("prod-1").quantity(2).price(new BigDecimal("10.00")).build(),
                            OrderDto.ItemRequest.builder()
                                    .productId("prod-2").quantity(1).price(new BigDecimal("25.00")).build()
                    ))
                    .shippingAddress(OrderDto.AddressRequest.builder()
                            .street("123 Main").city("Plano").state("TX").zipCode("75024").build())
                    .build();

            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
                Order order = invocation.getArgument(0);
                order.setId(UUID.randomUUID());
                order.setCreatedAt(Instant.now());
                order.setUpdatedAt(Instant.now());
                return order;
            });
            when(outboxEventRepository.save(any())).thenReturn(null);

            // When
            OrderDto.Response response = orderService.createOrder(request);

            // Then
            assertThat(response.getStatus()).isEqualTo("CREATED");
            assertThat(response.getTotalAmount()).isEqualByComparingTo(new BigDecimal("45.00"));
            assertThat(response.getItems()).hasSize(2);
            assertThat(response.getCustomerId()).isEqualTo("cust-123");

            verify(orderRepository).save(any(Order.class));
            verify(outboxEventRepository).save(any());
        }
    }

    @Nested
    @DisplayName("getOrder")
    class GetOrder {

        @Test
        @DisplayName("should throw OrderNotFoundException when order does not exist")
        void shouldThrowWhenNotFound() {
            UUID orderId = UUID.randomUUID();
            when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.getOrder(orderId))
                    .isInstanceOf(OrderNotFoundException.class)
                    .hasMessageContaining(orderId.toString());
        }
    }

    @Nested
    @DisplayName("cancelOrder")
    class CancelOrder {

        @Test
        @DisplayName("should cancel a CREATED order")
        void shouldCancelCreatedOrder() {
            UUID orderId = UUID.randomUUID();
            Order order = buildOrder(orderId, OrderStatus.CREATED);

            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
            when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(outboxEventRepository.save(any())).thenReturn(null);

            OrderDto.Response response = orderService.cancelOrder(orderId);

            assertThat(response.getStatus()).isEqualTo("CANCELLED");
        }

        @Test
        @DisplayName("should fail to cancel a SHIPPED order")
        void shouldFailToCancelShippedOrder() {
            UUID orderId = UUID.randomUUID();
            Order order = buildOrder(orderId, OrderStatus.SHIPPED);

            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.cancelOrder(orderId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition");
        }
    }

    private Order buildOrder(UUID id, OrderStatus status) {
        Order order = Order.builder()
                .id(id)
                .customerId("cust-1")
                .status(status)
                .totalAmount(new BigDecimal("50.00"))
                .shippingAddress(ShippingAddress.builder()
                        .street("1 St").city("Plano").state("TX").zipCode("75024").build())
                .build();
        OrderItem item = OrderItem.builder()
                .id(UUID.randomUUID())
                .productId("p1")
                .quantity(1)
                .price(new BigDecimal("50.00"))
                .build();
        order.addItem(item);
        return order;
    }
}

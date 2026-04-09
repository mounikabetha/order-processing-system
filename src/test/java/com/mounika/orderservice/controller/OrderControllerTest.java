package com.mounika.orderservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mounika.orderservice.dto.OrderDto;
import com.mounika.orderservice.exception.OrderNotFoundException;
import com.mounika.orderservice.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@AutoConfigureMockMvc(addFilters = false)
class OrderControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean  private OrderService orderService;

    @Test
    @DisplayName("POST /api/v1/orders - should create order and return 201")
    void shouldCreateOrder() throws Exception {
        OrderDto.CreateRequest request = OrderDto.CreateRequest.builder()
                .customerId("cust-1")
                .items(List.of(OrderDto.ItemRequest.builder()
                        .productId("p1").quantity(1).price(new BigDecimal("10.00")).build()))
                .shippingAddress(OrderDto.AddressRequest.builder()
                        .street("1 St").city("Plano").state("TX").zipCode("75024").build())
                .build();

        OrderDto.Response response = OrderDto.Response.builder()
                .id(UUID.randomUUID()).customerId("cust-1").status("CREATED")
                .totalAmount(new BigDecimal("10.00"))
                .items(List.of()).createdAt(Instant.now()).updatedAt(Instant.now()).build();

        when(orderService.createOrder(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"));
    }

    @Test
    @DisplayName("POST /api/v1/orders - should return 400 for invalid request")
    void shouldRejectInvalidRequest() throws Exception {
        String invalidJson = """
                {"customerId": "", "items": []}
                """;

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/orders/{id} - should return 404 for unknown order")
    void shouldReturn404() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderService.getOrder(orderId)).thenThrow(new OrderNotFoundException(orderId));

        mockMvc.perform(get("/api/v1/orders/{id}", orderId))
                .andExpect(status().isNotFound());
    }
}

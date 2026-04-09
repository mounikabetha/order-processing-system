package com.mounika.orderservice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mounika.orderservice.dto.OrderDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@EmbeddedKafka(partitions = 1, topics = {"order.events"})
@ActiveProfiles("test")
class OrderIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    @DisplayName("Full order lifecycle: create → get → cancel")
    void orderLifecycle() throws Exception {
        OrderDto.CreateRequest request = OrderDto.CreateRequest.builder()
                .customerId("integration-cust-1")
                .items(List.of(OrderDto.ItemRequest.builder()
                        .productId("prod-1").quantity(3).price(new BigDecimal("15.00")).build()))
                .shippingAddress(OrderDto.AddressRequest.builder()
                        .street("100 Test Ave").city("Dallas").state("TX").zipCode("75001").build())
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.totalAmount").value(45.00))
                .andReturn();

        OrderDto.Response created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), OrderDto.Response.class);

        mockMvc.perform(get("/api/v1/orders/{id}", created.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value("integration-cust-1"));

        mockMvc.perform(patch("/api/v1/orders/{id}/cancel", created.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }
}
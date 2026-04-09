package com.mounika.orderservice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mounika.orderservice.dto.OrderDto;
import com.mounika.orderservice.entity.Order;
import com.mounika.orderservice.enums.OrderStatus;
import com.mounika.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = {"order.events"})
@ActiveProfiles("test")
class OrderIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("orderdb_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> "6379");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private OrderRepository orderRepository;

    @Test
    @DisplayName("Full order lifecycle: create → get → cancel")
    void orderLifecycle() throws Exception {
        // Create
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

        // Get
        mockMvc.perform(get("/api/v1/orders/{id}", created.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value("integration-cust-1"));

        // Cancel
        mockMvc.perform(patch("/api/v1/orders/{id}/cancel", created.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // Verify in DB
        Order dbOrder = orderRepository.findById(created.getId()).orElseThrow();
        assertThat(dbOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }
}

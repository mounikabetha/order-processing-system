package com.mounika.orderservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class OrderDto {

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CreateRequest {
        @NotBlank(message = "Customer ID is required")
        private String customerId;

        @NotEmpty(message = "At least one item is required")
        @Valid
        private List<ItemRequest> items;

        @NotNull @Valid
        private AddressRequest shippingAddress;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ItemRequest {
        @NotBlank private String productId;
        @Min(1) private int quantity;
        @DecimalMin("0.01") private BigDecimal price;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class AddressRequest {
        @NotBlank private String street;
        @NotBlank private String city;
        @NotBlank private String state;
        @NotBlank private String zipCode;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Response {
        private UUID id;
        private String customerId;
        private String status;
        private BigDecimal totalAmount;
        private List<ItemResponse> items;
        private AddressResponse shippingAddress;
        private Instant createdAt;
        private Instant updatedAt;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ItemResponse {
        private UUID id;
        private String productId;
        private int quantity;
        private BigDecimal price;
        private BigDecimal subtotal;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class AddressResponse {
        private String street;
        private String city;
        private String state;
        private String zipCode;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class EventResponse {
        private UUID eventId;
        private String eventType;
        private String previousStatus;
        private String currentStatus;
        private Instant timestamp;
    }
}

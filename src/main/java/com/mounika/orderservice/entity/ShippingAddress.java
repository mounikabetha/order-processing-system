package com.mounika.orderservice.entity;

import jakarta.persistence.Embeddable;
import lombok.*;

@Embeddable
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShippingAddress {
    private String street;
    private String city;
    private String state;
    private String zipCode;
}

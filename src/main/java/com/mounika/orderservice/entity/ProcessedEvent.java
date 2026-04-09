package com.mounika.orderservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedEvent {

    @Id
    private UUID eventId;

    @Column(nullable = false)
    private String consumerGroup;

    @CreationTimestamp
    private Instant processedAt;
}

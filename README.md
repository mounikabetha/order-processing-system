# Event-Driven Order Processing System

A production-grade event-driven order processing microservice built with **Java 17**, **Spring Boot 3**, **Apache Kafka**, **PostgreSQL**, and **Redis**. Demonstrates CQRS, idempotent event consumers, transactional outbox pattern, and dead-letter queue handling.

![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-green)
![Kafka](https://img.shields.io/badge/Kafka-3.6-black)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue)
![Redis](https://img.shields.io/badge/Redis-7-red)
![License](https://img.shields.io/badge/License-MIT-yellow)

## Architecture

```
┌──────────────┐     ┌──────────────┐     ┌──────────────────┐
│  REST API     │────▶│  Order       │────▶│  Outbox Table    │
│  Controller   │     │  Service     │     │  (PostgreSQL)    │
└──────────────┘     └──────────────┘     └────────┬─────────┘
                                                    │
                                          ┌─────────▼─────────┐
                                          │  Outbox Poller     │
                                          │  (Scheduled)       │
                                          └─────────┬─────────┘
                                                    │
                     ┌──────────────────────────────▼──────────┐
                     │            Apache Kafka                  │
                     │  ┌────────────┐  ┌───────────────────┐  │
                     │  │ order.     │  │ order.events.dlq  │  │
                     │  │ events     │  │ (dead letter)     │  │
                     │  └─────┬──────┘  └───────────────────┘  │
                     └────────┼────────────────────────────────┘
               ┌──────────────┼──────────────┐
               ▼              ▼              ▼
        ┌────────────┐ ┌────────────┐ ┌────────────┐
        │  Payment   │ │ Inventory  │ │Notification│
        │  Consumer  │ │ Consumer   │ │ Consumer   │
        └────────────┘ └────────────┘ └────────────┘
```

## Key Design Patterns

| Pattern | Implementation |
|---|---|
| **Event-Driven Architecture** | Kafka topics for async order state transitions |
| **Transactional Outbox** | Events written to outbox table in same DB transaction as order, then polled to Kafka |
| **Idempotent Consumer** | Deduplication via `event_id` tracking in `processed_events` table |
| **Dead Letter Queue** | Failed events routed to DLQ after 3 retries with exponential backoff |
| **CQRS (lightweight)** | Write model via commands, read model via cached queries |
| **Cache-Aside** | Redis cache for order reads with TTL-based invalidation |

## Tech Stack

- **Runtime**: Java 17, Spring Boot 3.2
- **Messaging**: Apache Kafka 3.6 with Spring Kafka
- **Database**: PostgreSQL 16 with Flyway migrations
- **Caching**: Redis 7 with Spring Data Redis
- **Auth**: JWT with Spring Security
- **API Docs**: OpenAPI 3 / Swagger UI
- **Observability**: Micrometer + Prometheus metrics, structured logging
- **Testing**: JUnit 5, Mockito, Testcontainers, REST Assured
- **Infra**: Docker Compose, GitHub Actions CI

## Getting Started

### Prerequisites
- Java 17+
- Docker & Docker Compose

### Run Locally

```bash
# Start infrastructure (Kafka, PostgreSQL, Redis)
docker-compose up -d

# Run the application
./mvnw spring-boot:run

# Or run everything together
docker-compose --profile app up -d
```

### API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/orders` | Create a new order |
| `GET` | `/api/v1/orders/{id}` | Get order by ID |
| `GET` | `/api/v1/orders?customerId={id}` | List orders by customer |
| `PATCH` | `/api/v1/orders/{id}/cancel` | Cancel an order |
| `GET` | `/api/v1/orders/{id}/events` | Get order event history |
| `GET` | `/actuator/health` | Health check |
| `GET` | `/swagger-ui.html` | API documentation |

### Create an Order

```bash
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "cust-123",
    "items": [
      {"productId": "prod-001", "quantity": 2, "price": 29.99},
      {"productId": "prod-002", "quantity": 1, "price": 49.99}
    ],
    "shippingAddress": {
      "street": "123 Main St",
      "city": "Plano",
      "state": "TX",
      "zipCode": "75024"
    }
  }'
```

## Order State Machine

```
  CREATED ──▶ PAYMENT_PENDING ──▶ PAID ──▶ SHIPPED ──▶ DELIVERED
     │              │                │
     ▼              ▼                ▼
  CANCELLED    PAYMENT_FAILED    REFUNDED
```

## Project Structure

```
src/main/java/com/mounika/orderservice/
├── config/          # Kafka, Redis, Security, Swagger config
├── controller/      # REST controllers
├── dto/             # Request/Response DTOs
├── entity/          # JPA entities
├── enums/           # Order status, event types
├── event/           # Kafka event models
├── exception/       # Global exception handling
├── repository/      # Spring Data JPA repositories
├── service/         # Business logic
├── consumer/        # Kafka consumers (idempotent)
├── producer/        # Outbox-based event publishing
└── OrderServiceApplication.java
```

## Testing

```bash
# Unit tests
./mvnw test

# Integration tests (requires Docker for Testcontainers)
./mvnw verify -P integration-test

# Test coverage report
./mvnw jacoco:report
open target/site/jacoco/index.html
```

## Configuration

Key environment variables:

| Variable | Default | Description |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/orderdb` | Database URL |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka brokers |
| `SPRING_REDIS_HOST` | `localhost` | Redis host |
| `JWT_SECRET` | — | JWT signing secret |

## License

MIT

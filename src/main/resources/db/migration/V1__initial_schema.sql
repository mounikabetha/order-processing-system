-- V1: Core schema for order processing system

CREATE TABLE orders (
    id            UUID PRIMARY KEY,
    customer_id   VARCHAR(255) NOT NULL,
    status        VARCHAR(50)  NOT NULL,
    total_amount  NUMERIC(12, 2) NOT NULL,
    street        VARCHAR(255),
    city          VARCHAR(100),
    state         VARCHAR(50),
    zip_code      VARCHAR(20),
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_orders_customer_id ON orders (customer_id);
CREATE INDEX idx_orders_status ON orders (status);

CREATE TABLE order_items (
    id         UUID PRIMARY KEY,
    order_id   UUID           NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    product_id VARCHAR(255)   NOT NULL,
    quantity   INT            NOT NULL CHECK (quantity > 0),
    price      NUMERIC(10, 2) NOT NULL CHECK (price > 0)
);

CREATE INDEX idx_order_items_order_id ON order_items (order_id);

CREATE TABLE outbox_events (
    id           UUID PRIMARY KEY,
    aggregate_id VARCHAR(255)  NOT NULL,
    event_type   VARCHAR(100)  NOT NULL,
    payload      TEXT          NOT NULL,
    published    BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    published_at TIMESTAMPTZ
);

CREATE INDEX idx_outbox_published ON outbox_events (published, created_at);

CREATE TABLE processed_events (
    event_id       UUID         NOT NULL,
    consumer_group VARCHAR(255) NOT NULL,
    processed_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (event_id, consumer_group)
);

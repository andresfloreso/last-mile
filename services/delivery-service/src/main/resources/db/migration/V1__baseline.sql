-- =====================================================================
-- delivery-service · V1 baseline
-- Esquema equivalente al que generaba Hibernate (ddl-auto: update) a partir
-- de las entidades Customer, Shipment y DeliveryAttempt. Desde esta versión
-- el esquema se gestiona solo con Flyway; Hibernate únicamente lo valida.
--
-- Los enums JPA (@Enumerated(EnumType.STRING)) se guardan como VARCHAR con
-- un CHECK que limita los valores permitidos.
-- =====================================================================

-- ---------------------------------------------------------------------
-- customers
-- ---------------------------------------------------------------------
CREATE TABLE customers (
    id            UUID             NOT NULL,
    full_name     VARCHAR(255)     NOT NULL,
    phone         VARCHAR(255),
    address       VARCHAR(255)     NOT NULL,
    commune       VARCHAR(255),
    latitude      DOUBLE PRECISION,
    longitude     DOUBLE PRECISION,
    external_code VARCHAR(255),

    CONSTRAINT pk_customers PRIMARY KEY (id)
);

-- ShipmentService.createOrGetCustomer busca por código externo en cada import.
CREATE INDEX ix_customers_external_code ON customers (external_code);

-- ---------------------------------------------------------------------
-- shipments
-- ---------------------------------------------------------------------
CREATE TABLE shipments (
    id               UUID                        NOT NULL,
    tracking_code    VARCHAR(255)                NOT NULL,
    customer_id      UUID                        NOT NULL,
    scheduled_date   DATE                        NOT NULL,
    status           VARCHAR(255)                         DEFAULT 'PENDING',
    source           VARCHAR(255),
    notes            VARCHAR(255),
    route_position   INTEGER,
    stop_type        VARCHAR(255)                         DEFAULT 'DELIVERY',
    package_count    INTEGER                              DEFAULT 1,
    order_preference VARCHAR(255)                         DEFAULT 'AUTO',
    label            VARCHAR(255),
    created_at       TIMESTAMP(6) WITH TIME ZONE          DEFAULT now(),

    CONSTRAINT pk_shipments PRIMARY KEY (id),
    CONSTRAINT uk_shipments_tracking_code UNIQUE (tracking_code),
    CONSTRAINT fk_shipments_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT ck_shipments_status CHECK (status IN ('PENDING', 'ROUTED', 'OUT_FOR_DELIVERY', 'DELIVERED', 'REJECTED', 'RESCHEDULED')),
    CONSTRAINT ck_shipments_stop_type CHECK (stop_type IN ('DELIVERY', 'PICKUP')),
    CONSTRAINT ck_shipments_order_preference CHECK (order_preference IN ('FIRST', 'AUTO', 'LAST'))
);

-- Armado de ruta y listado diario: findByScheduledDate[OrderByRoutePositionAsc].
CREATE INDEX ix_shipments_scheduled_date ON shipments (scheduled_date, route_position);
CREATE INDEX ix_shipments_customer_id ON shipments (customer_id);

-- ---------------------------------------------------------------------
-- delivery_attempts
-- ---------------------------------------------------------------------
CREATE TABLE delivery_attempts (
    id               UUID                        NOT NULL,
    shipment_id      UUID                        NOT NULL,
    customer_id      UUID                        NOT NULL,
    attempted_at     TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    outcome          VARCHAR(255)                NOT NULL,
    rejection_reason VARCHAR(255),

    CONSTRAINT pk_delivery_attempts PRIMARY KEY (id),
    CONSTRAINT fk_delivery_attempts_shipment FOREIGN KEY (shipment_id) REFERENCES shipments (id),
    CONSTRAINT fk_delivery_attempts_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT ck_delivery_attempts_outcome CHECK (outcome IN ('ACCEPTED', 'REJECTED_NOT_HOME', 'REJECTED_REFUSED', 'RESCHEDULED_BY_CUSTOMER'))
);

-- TimeWindowLearningService arma el histograma por cliente: findByCustomerId.
CREATE INDEX ix_delivery_attempts_customer_id ON delivery_attempts (customer_id, attempted_at);
CREATE INDEX ix_delivery_attempts_shipment_id ON delivery_attempts (shipment_id);

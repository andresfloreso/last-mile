-- =====================================================================
-- auth-service · V1 baseline
-- Esquema equivalente al que generaba Hibernate (ddl-auto: update) a partir
-- de la entidad cl.lastmile.auth.model.User. Desde esta versión el esquema
-- se gestiona solo con Flyway; Hibernate únicamente lo valida.
-- =====================================================================

CREATE TABLE users (
    id            UUID                        NOT NULL,
    full_name     VARCHAR(255)                NOT NULL,
    email         VARCHAR(255)                NOT NULL,
    password_hash VARCHAR(255)                NOT NULL,
    role          VARCHAR(255)                NOT NULL DEFAULT 'OPERATOR',
    enabled       BOOLEAN                     NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP(6) WITH TIME ZONE          DEFAULT now(),

    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('ADMIN', 'DISPATCHER', 'DRIVER', 'OPERATOR', 'CLIENT'))
);

-- Búsqueda de usuarios por rol (p.ej. "¿existe algún ADMIN?").
CREATE INDEX ix_users_role ON users (role);

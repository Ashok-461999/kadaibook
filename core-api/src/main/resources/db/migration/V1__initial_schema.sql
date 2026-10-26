-- KadaiBook initial schema. Money is BIGINT paise everywhere; no floating point.
-- shop_id is present on every shop-owned table from day one.

CREATE TABLE users (
    id            UUID PRIMARY KEY,
    phone         VARCHAR(20)  NOT NULL,
    display_name  VARCHAR(120) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_users_phone UNIQUE (phone)
);

CREATE TABLE shops (
    id            UUID PRIMARY KEY,
    name          VARCHAR(160) NOT NULL,
    owner_user_id UUID         NOT NULL REFERENCES users (id),
    consent_at    TIMESTAMPTZ  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_shops_owner ON shops (owner_user_id);

CREATE TABLE customers (
    id              UUID PRIMARY KEY,
    shop_id         UUID         NOT NULL REFERENCES shops (id),
    display_name    VARCHAR(200) NOT NULL,
    normalized_name VARCHAR(200) NOT NULL,
    aliases         JSONB        NOT NULL DEFAULT '[]'::jsonb,
    created_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_customers_shop_name UNIQUE (shop_id, normalized_name)
);
CREATE INDEX idx_customers_shop ON customers (shop_id);

CREATE TABLE notebook_pages (
    id                UUID PRIMARY KEY,
    shop_id           UUID         NOT NULL REFERENCES shops (id),
    s3_key            VARCHAR(512) NOT NULL,
    image_sha256      VARCHAR(64)  NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type      VARCHAR(100) NOT NULL,
    size_bytes        BIGINT       NOT NULL,
    status            VARCHAR(30)  NOT NULL,
    layout_type       VARCHAR(30),
    image_deleted_at  TIMESTAMPTZ,
    imported_at       TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL,
    version           BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_pages_shop_hash UNIQUE (shop_id, image_sha256),
    CONSTRAINT ck_pages_size CHECK (size_bytes > 0)
);
CREATE INDEX idx_pages_shop_created ON notebook_pages (shop_id, created_at DESC);
CREATE INDEX idx_pages_status ON notebook_pages (status);

CREATE TABLE extraction_runs (
    id                    UUID PRIMARY KEY,
    page_id               UUID         NOT NULL REFERENCES notebook_pages (id),
    model                 VARCHAR(160),
    prompt_version        VARCHAR(60),
    schema_version        VARCHAR(20),
    raw_json              TEXT,
    attempts              INT          NOT NULL DEFAULT 0,
    latency_ms            BIGINT       NOT NULL DEFAULT 0,
    cost_paise            BIGINT       NOT NULL DEFAULT 0,
    input_tokens          BIGINT       NOT NULL DEFAULT 0,
    output_tokens         BIGINT       NOT NULL DEFAULT 0,
    status                VARCHAR(30)  NOT NULL,
    error                 TEXT,
    layout_confidence     DOUBLE PRECISION,
    opening_balance_paise BIGINT,
    closing_balance_paise BIGINT,
    page_customer_name    VARCHAR(200),
    page_customer_status  VARCHAR(20),
    page_issues           JSONB        NOT NULL DEFAULT '[]'::jsonb,
    review_required       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_runs_page_created ON extraction_runs (page_id, created_at DESC);
CREATE INDEX idx_runs_status ON extraction_runs (status);

CREATE TABLE extracted_entries (
    id                     UUID PRIMARY KEY,
    run_id                 UUID          NOT NULL REFERENCES extraction_runs (id),
    entry_index            INT           NOT NULL,
    entry_date             DATE,
    date_status            VARCHAR(20)   NOT NULL,
    description            TEXT,
    debit_paise            BIGINT,
    credit_paise           BIGINT,
    entry_status           VARCHAR(20)   NOT NULL,
    issues                 JSONB         NOT NULL DEFAULT '[]'::jsonb,
    source_text            TEXT,
    running_balance_paise  BIGINT,
    customer_name_value    VARCHAR(200),
    customer_name_status   VARCHAR(20),
    created_at             TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_entries_run_index UNIQUE (run_id, entry_index),
    CONSTRAINT ck_entries_debit CHECK (debit_paise IS NULL OR debit_paise >= 0),
    CONSTRAINT ck_entries_credit CHECK (credit_paise IS NULL OR credit_paise >= 0)
);
CREATE INDEX idx_entries_run ON extracted_entries (run_id, entry_index);

CREATE TABLE reviewed_entries (
    id                   UUID PRIMARY KEY,
    extracted_entry_id   UUID         NOT NULL REFERENCES extracted_entries (id),
    reviewer_id          UUID         NOT NULL REFERENCES users (id),
    action               VARCHAR(20)  NOT NULL,
    final_date           DATE,
    final_date_status    VARCHAR(20),
    final_description    TEXT,
    final_debit_paise    BIGINT,
    final_credit_paise   BIGINT,
    final_entry_status   VARCHAR(20),
    customer_id          UUID REFERENCES customers (id),
    note                 TEXT,
    decided_at           TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_reviewed_extracted UNIQUE (extracted_entry_id),
    CONSTRAINT ck_reviewed_debit CHECK (final_debit_paise IS NULL OR final_debit_paise >= 0),
    CONSTRAINT ck_reviewed_credit CHECK (final_credit_paise IS NULL OR final_credit_paise >= 0)
);
CREATE INDEX idx_reviewed_customer ON reviewed_entries (customer_id);

CREATE TABLE ledger_entries (
    id                       UUID PRIMARY KEY,
    shop_id                  UUID        NOT NULL REFERENCES shops (id),
    customer_id              UUID        NOT NULL REFERENCES customers (id),
    entry_date               DATE,
    description              TEXT        NOT NULL,
    debit_paise              BIGINT      NOT NULL,
    credit_paise             BIGINT      NOT NULL,
    source_reviewed_entry_id UUID REFERENCES reviewed_entries (id),
    source_page_id           UUID REFERENCES notebook_pages (id),
    created_at               TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_ledger_reviewed_source UNIQUE (source_reviewed_entry_id),
    CONSTRAINT ck_ledger_non_negative CHECK (debit_paise >= 0 AND credit_paise >= 0),
    CONSTRAINT ck_ledger_direction CHECK ((debit_paise > 0) <> (credit_paise > 0))
);
CREATE INDEX idx_ledger_shop_customer ON ledger_entries (shop_id, customer_id, entry_date);
CREATE INDEX idx_ledger_source_page ON ledger_entries (source_page_id);

CREATE TABLE balance_confirmations (
    id                       UUID PRIMARY KEY,
    shop_id                  UUID        NOT NULL REFERENCES shops (id),
    page_id                  UUID        NOT NULL REFERENCES notebook_pages (id),
    customer_id              UUID        NOT NULL REFERENCES customers (id),
    confirmed_balance_paise  BIGINT      NOT NULL,
    calculated_balance_paise BIGINT      NOT NULL,
    confirmed_by             UUID        NOT NULL REFERENCES users (id),
    confirmed_at             TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_balance_confirmation UNIQUE (page_id, customer_id)
);

CREATE TABLE audit_log (
    id            UUID PRIMARY KEY,
    shop_id       UUID        NOT NULL REFERENCES shops (id),
    actor_user_id UUID REFERENCES users (id),
    action        VARCHAR(80) NOT NULL,
    entity_type   VARCHAR(60) NOT NULL,
    entity_id     UUID,
    before_json   TEXT,
    after_json    TEXT,
    at            TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_audit_shop_at ON audit_log (shop_id, at DESC);

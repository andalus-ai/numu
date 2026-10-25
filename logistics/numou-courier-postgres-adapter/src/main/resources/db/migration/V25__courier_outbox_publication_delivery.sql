-- M25: isolate external publication state from DB-local projections that use published_at.
-- One delivery row per event and destination supports independent subscribers and replay.
CREATE TABLE courier_event_publications (
    tenant_id UUID NOT NULL,
    event_id UUID NOT NULL,
    destination VARCHAR(120) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    available_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    lease_owner VARCHAR(160),
    lease_until TIMESTAMPTZ,
    published_at TIMESTAMPTZ,
    last_error VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, event_id, destination),
    FOREIGN KEY (tenant_id, event_id) REFERENCES courier_event_outbox (tenant_id, id),
    CONSTRAINT ck_courier_publication_destination CHECK (length(trim(destination)) BETWEEN 1 AND 120),
    CONSTRAINT ck_courier_publication_status CHECK (status IN ('PENDING', 'PUBLISHED', 'DEAD')),
    CONSTRAINT ck_courier_publication_attempts CHECK (attempt_count >= 0),
    CONSTRAINT ck_courier_publication_lease_pair CHECK ((lease_owner IS NULL) = (lease_until IS NULL)),
    CONSTRAINT ck_courier_publication_published_state CHECK ((status = 'PUBLISHED') = (published_at IS NOT NULL)),
    CONSTRAINT ck_courier_publication_terminal_unleased CHECK (status <> 'PUBLISHED' OR lease_owner IS NULL)
);

CREATE INDEX ix_courier_publication_ready
    ON courier_event_publications (destination, available_at, created_at)
    WHERE status = 'PENDING';

CREATE INDEX ix_courier_publication_expired_lease
    ON courier_event_publications (lease_until)
    WHERE status = 'PENDING' AND lease_owner IS NOT NULL;

-- Do not depend on courier_event_outbox.published_at: that column is also the
-- acknowledgement used by the in-database POD tracking projector.

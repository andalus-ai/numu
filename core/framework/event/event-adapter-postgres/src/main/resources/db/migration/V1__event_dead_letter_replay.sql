CREATE TABLE event_dead_letter_record (
 tenant_id UUID NOT NULL, cluster_id VARCHAR(200) NOT NULL, source_topic VARCHAR(249) NOT NULL,
 source_partition INTEGER NOT NULL CHECK(source_partition>=0), source_offset BIGINT NOT NULL CHECK(source_offset>=0),
 event_type VARCHAR(200) NOT NULL, payload TEXT NOT NULL, failure_reason VARCHAR(1000) NOT NULL,
 failed_at TIMESTAMPTZ NOT NULL, replay_status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
 CHECK(replay_status IN ('PENDING','CLAIMED','FAILED','PUBLISHED')),
 claim_token UUID, lease_until TIMESTAMPTZ, claimed_by VARCHAR(200), replay_reason VARCHAR(500),
 replay_attempts INTEGER NOT NULL DEFAULT 0 CHECK(replay_attempts>=0), published_at TIMESTAMPTZ,
 last_error VARCHAR(1000), created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(tenant_id,cluster_id,source_topic,source_partition,source_offset),
 CHECK((replay_status='CLAIMED')=(claim_token IS NOT NULL AND lease_until IS NOT NULL))
);
CREATE INDEX idx_event_dlq_recovery ON event_dead_letter_record(tenant_id,replay_status,lease_until,failed_at);
CREATE TABLE event_dead_letter_replay_audit (
 audit_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, tenant_id UUID NOT NULL,
 cluster_id VARCHAR(200) NOT NULL, source_topic VARCHAR(249) NOT NULL, source_partition INTEGER NOT NULL,
 source_offset BIGINT NOT NULL, claim_token UUID NOT NULL, actor_id VARCHAR(200) NOT NULL,
 reason VARCHAR(500) NOT NULL, outcome VARCHAR(20) NOT NULL CHECK(outcome IN ('CLAIMED','PUBLISHED','FAILED')),
 detail VARCHAR(1000), occurred_at TIMESTAMPTZ NOT NULL,
 UNIQUE(tenant_id,cluster_id,source_topic,source_partition,source_offset,claim_token,outcome)
);
CREATE INDEX idx_event_dlq_audit_tenant_time ON event_dead_letter_replay_audit(tenant_id,occurred_at DESC);

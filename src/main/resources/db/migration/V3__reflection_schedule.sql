CREATE TABLE reflection_schedule (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    enabled BOOLEAN NOT NULL,
    cron VARCHAR(120)
);
INSERT INTO reflection_schedule (id, enabled) VALUES (1, TRUE);

CREATE TABLE reflection_run (
    id UUID PRIMARY KEY,
    trigger_type VARCHAR(12) NOT NULL,
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at TIMESTAMP WITH TIME ZONE,
    summary VARCHAR(300) NOT NULL
);
CREATE INDEX idx_reflection_run_recent ON reflection_run(started_at DESC, id DESC);

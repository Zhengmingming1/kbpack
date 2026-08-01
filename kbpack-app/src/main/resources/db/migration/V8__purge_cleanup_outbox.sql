CREATE TABLE purge_cleanup_job (
    id                  uuid PRIMARY KEY,
    package_id          uuid NOT NULL,
    version_ids         jsonb NOT NULL DEFAULT '[]'::jsonb,
    storage_objects     jsonb NOT NULL DEFAULT '[]'::jsonb,
    status              varchar(16) NOT NULL DEFAULT 'pending'
                            CHECK (status IN ('pending', 'retry', 'completed')),
    attempts            integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at     timestamptz NOT NULL DEFAULT now(),
    last_error          text,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    completed_at        timestamptz
);

CREATE INDEX idx_purge_cleanup_job_ready
    ON purge_cleanup_job(status, next_attempt_at, created_at)
    WHERE status IN ('pending', 'retry');

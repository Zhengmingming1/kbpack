ALTER TABLE knowledge_package
    ADD COLUMN lock_version bigint NOT NULL DEFAULT 0;

ALTER TABLE package_version
    ADD COLUMN lock_version bigint NOT NULL DEFAULT 0;

CREATE UNIQUE INDEX uq_parse_task_active_version
    ON parse_task(version_id)
    WHERE status IN ('pending', 'processing', 'retry_scheduled');

ALTER TABLE package_version
    ADD COLUMN promote_on_success boolean NOT NULL DEFAULT false;

CREATE UNIQUE INDEX uq_package_version_promote_on_success
    ON package_version(package_id)
    WHERE promote_on_success = true AND deleted_at IS NULL;

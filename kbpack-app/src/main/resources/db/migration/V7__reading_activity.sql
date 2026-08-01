-- Per-user reading state. Soft-deleted packages keep this data so a restored
-- package resumes where the user left off; physical deletion cascades it.

CREATE TABLE reading_progress (
    user_id             uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    package_id          uuid NOT NULL REFERENCES knowledge_package(id) ON DELETE CASCADE,
    version_id          uuid NOT NULL REFERENCES package_version(id) ON DELETE CASCADE,
    document_id         uuid NOT NULL REFERENCES extracted_document(id) ON DELETE CASCADE,
    anchor              varchar(256),
    progress_percent    numeric(5,2) NOT NULL DEFAULT 0
                            CHECK (progress_percent >= 0 AND progress_percent <= 100),
    scroll_offset       bigint NOT NULL DEFAULT 0 CHECK (scroll_offset >= 0),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, document_id)
);
CREATE INDEX idx_reading_progress_updated
    ON reading_progress(user_id, updated_at DESC);

CREATE TABLE recent_view (
    user_id             uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    package_id          uuid NOT NULL REFERENCES knowledge_package(id) ON DELETE CASCADE,
    version_id          uuid NOT NULL REFERENCES package_version(id) ON DELETE CASCADE,
    document_id         uuid NOT NULL REFERENCES extracted_document(id) ON DELETE CASCADE,
    viewed_at           timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, document_id)
);
CREATE INDEX idx_recent_view_user_viewed
    ON recent_view(user_id, viewed_at DESC);

CREATE TABLE reading_bookmark (
    id                  uuid PRIMARY KEY,
    user_id             uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    package_id          uuid NOT NULL REFERENCES knowledge_package(id) ON DELETE CASCADE,
    version_id          uuid NOT NULL REFERENCES package_version(id) ON DELETE CASCADE,
    document_id         uuid NOT NULL REFERENCES extracted_document(id) ON DELETE CASCADE,
    anchor              varchar(256) NOT NULL DEFAULT '',
    label               varchar(256),
    note                text,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_reading_bookmark_location UNIQUE (user_id, document_id, anchor)
);
CREATE INDEX idx_reading_bookmark_user_created
    ON reading_bookmark(user_id, created_at DESC);
CREATE INDEX idx_reading_bookmark_user_package
    ON reading_bookmark(user_id, package_id, created_at DESC);

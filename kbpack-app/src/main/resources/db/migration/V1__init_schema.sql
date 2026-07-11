-- V1 init schema (docs/03-database-design.md)
-- UUIDs are generated in the application layer (UUID v7); no DEFAULT gen_random_uuid().

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ========== 用户 ==========
CREATE TABLE app_user (
    id                  uuid PRIMARY KEY,
    username            varchar(64) NOT NULL UNIQUE,
    password_hash       varchar(255) NOT NULL,
    display_name        varchar(128),
    role                varchar(16) NOT NULL DEFAULT 'viewer'
                            CHECK (role IN ('owner','admin','editor','viewer')),
    status              varchar(16) NOT NULL DEFAULT 'active'
                            CHECK (status IN ('active','locked','disabled')),
    failed_login_count  int NOT NULL DEFAULT 0,
    locked_until        timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now()
);

-- ========== 知识包 ==========
CREATE TABLE knowledge_package (
    id                  uuid PRIMARY KEY,
    title               varchar(256) NOT NULL,
    slug                varchar(256) NOT NULL UNIQUE,
    description         text,
    cover_asset_path    varchar(512),
    source_type         varchar(32) NOT NULL DEFAULT 'manual'
                            CHECK (source_type IN ('ai','local','web_archive','manual')),
    source_name         varchar(64),
    status              varchar(16) NOT NULL DEFAULT 'draft'
                            CHECK (status IN ('draft','active','deprecated','archived')),
    visibility          varchar(16) NOT NULL DEFAULT 'private'
                            CHECK (visibility IN ('private','team','public')),
    owner_id            uuid NOT NULL REFERENCES app_user(id),
    current_version_id  uuid,
    quality_meta        jsonb,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    deleted_at          timestamptz
);
CREATE INDEX idx_knowledge_package_owner ON knowledge_package(owner_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_knowledge_package_status ON knowledge_package(status) WHERE deleted_at IS NULL;
CREATE INDEX idx_knowledge_package_updated_at ON knowledge_package(updated_at DESC) WHERE deleted_at IS NULL;

-- ========== 版本 ==========
CREATE TABLE package_version (
    id                  uuid PRIMARY KEY,
    package_id          uuid NOT NULL REFERENCES knowledge_package(id) ON DELETE CASCADE,
    version_no          int NOT NULL,
    original_filename   varchar(512) NOT NULL,
    content_hash        varchar(64) NOT NULL,
    entry_file          varchar(512),
    storage_path        varchar(512) NOT NULL,
    unpacked_size       bigint,
    file_count          int,
    parse_status        varchar(16) NOT NULL DEFAULT 'pending'
                            CHECK (parse_status IN ('pending','processing','success','failed')),
    parse_error         text,
    created_by          uuid NOT NULL REFERENCES app_user(id),
    created_at          timestamptz NOT NULL DEFAULT now(),
    deleted_at          timestamptz,
    UNIQUE (package_id, version_no)
);
CREATE INDEX idx_package_version_package ON package_version(package_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_package_version_parse_status ON package_version(parse_status);
CREATE INDEX idx_package_version_hash ON package_version(package_id, content_hash);

ALTER TABLE knowledge_package
    ADD CONSTRAINT fk_current_version FOREIGN KEY (current_version_id)
    REFERENCES package_version(id);

-- ========== 包内资源 ==========
CREATE TABLE package_asset (
    id          uuid PRIMARY KEY,
    version_id  uuid NOT NULL REFERENCES package_version(id) ON DELETE CASCADE,
    path        varchar(1024) NOT NULL,
    mime_type   varchar(128),
    size        bigint NOT NULL,
    sha256      varchar(64) NOT NULL,
    role        varchar(16) NOT NULL DEFAULT 'other'
                    CHECK (role IN ('entry','html','markdown','image','script','style','data','other'))
);
CREATE INDEX idx_package_asset_version ON package_asset(version_id);

-- ========== 抽取文档 ==========
CREATE TABLE extracted_document (
    id              uuid PRIMARY KEY,
    version_id      uuid NOT NULL REFERENCES package_version(id) ON DELETE CASCADE,
    package_id      uuid NOT NULL REFERENCES knowledge_package(id) ON DELETE CASCADE,
    source_path     varchar(1024) NOT NULL,
    title           varchar(512),
    doc_type        varchar(16) NOT NULL
                        CHECK (doc_type IN ('markdown','html','content_js','text')),
    order_no        int NOT NULL DEFAULT 0,
    content         text NOT NULL,
    raw_content     text,
    heading_tree    jsonb,
    word_count      int,
    created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_extracted_document_version ON extracted_document(version_id, order_no);
CREATE INDEX idx_extracted_document_package ON extracted_document(package_id);
CREATE INDEX idx_extracted_document_heading_tree ON extracted_document USING gin(heading_tree);

-- ========== 搜索分块 ==========
CREATE TABLE search_chunk (
    id              uuid PRIMARY KEY,
    document_id     uuid NOT NULL REFERENCES extracted_document(id) ON DELETE CASCADE,
    package_id      uuid NOT NULL REFERENCES knowledge_package(id) ON DELETE CASCADE,
    version_id      uuid NOT NULL REFERENCES package_version(id) ON DELETE CASCADE,
    chunk_index     int NOT NULL,
    heading         varchar(512),
    content         text NOT NULL,
    token_count     int,
    anchor          varchar(256)
);
CREATE INDEX idx_search_chunk_document ON search_chunk(document_id, chunk_index);
CREATE INDEX idx_search_chunk_package ON search_chunk(package_id);

-- ========== 标签 / 集合 ==========
CREATE TABLE tag (
    id          uuid PRIMARY KEY,
    name        varchar(64) NOT NULL UNIQUE,
    created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE package_tag (
    package_id  uuid NOT NULL REFERENCES knowledge_package(id) ON DELETE CASCADE,
    tag_id      uuid NOT NULL REFERENCES tag(id) ON DELETE CASCADE,
    PRIMARY KEY (package_id, tag_id)
);
CREATE INDEX idx_package_tag_tag ON package_tag(tag_id);

CREATE TABLE collection (
    id          uuid PRIMARY KEY,
    name        varchar(128) NOT NULL,
    parent_id   uuid REFERENCES collection(id) ON DELETE CASCADE,
    sort_order  int NOT NULL DEFAULT 0,
    created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_collection_parent ON collection(parent_id);

CREATE TABLE package_collection (
    package_id      uuid NOT NULL REFERENCES knowledge_package(id) ON DELETE CASCADE,
    collection_id   uuid NOT NULL REFERENCES collection(id) ON DELETE CASCADE,
    PRIMARY KEY (package_id, collection_id)
);
CREATE INDEX idx_package_collection_collection ON package_collection(collection_id);

-- ========== 收藏 ==========
CREATE TABLE favorite (
    user_id     uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    package_id  uuid NOT NULL REFERENCES knowledge_package(id) ON DELETE CASCADE,
    created_at  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, package_id)
);

-- ========== 解析任务 ==========
CREATE TABLE parse_task (
    id              uuid PRIMARY KEY,
    version_id      uuid NOT NULL REFERENCES package_version(id) ON DELETE CASCADE,
    task_type       varchar(32) NOT NULL DEFAULT 'parse'
                        CHECK (task_type IN ('parse','reparse','reindex')),
    status          varchar(20) NOT NULL DEFAULT 'pending'
                        CHECK (status IN ('pending','processing','success','failed','retry_scheduled')),
    attempt_count   int NOT NULL DEFAULT 0,
    max_attempts    int NOT NULL DEFAULT 3,
    next_retry_at   timestamptz,
    error_message   text,
    started_at      timestamptz,
    finished_at     timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_parse_task_poll ON parse_task(status, next_retry_at);
CREATE INDEX idx_parse_task_version ON parse_task(version_id);

-- ========== 操作日志 ==========
CREATE TABLE operation_log (
    id          uuid PRIMARY KEY,
    user_id     uuid REFERENCES app_user(id),
    action      varchar(64) NOT NULL,
    target_type varchar(32),
    target_id   uuid,
    detail      jsonb,
    ip          varchar(64),
    created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_operation_log_target ON operation_log(target_type, target_id);
CREATE INDEX idx_operation_log_created_at ON operation_log(created_at DESC);

-- ========== 系统设置 ==========
CREATE TABLE system_setting (
    key         varchar(128) PRIMARY KEY,
    value       jsonb NOT NULL,
    updated_at  timestamptz NOT NULL DEFAULT now()
);

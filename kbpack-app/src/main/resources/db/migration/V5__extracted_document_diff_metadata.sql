ALTER TABLE extracted_document
    ADD COLUMN content_hash varchar(64),
    ADD COLUMN content_length bigint;

UPDATE extracted_document
   SET content_hash = encode(digest(convert_to(content, 'UTF8'), 'sha256'), 'hex'),
       content_length = char_length(content);

ALTER TABLE extracted_document
    ALTER COLUMN content_hash SET NOT NULL,
    ALTER COLUMN content_length SET NOT NULL;

CREATE INDEX idx_extracted_document_version_source_path
    ON extracted_document(version_id, source_path, order_no, id);

CREATE INDEX idx_package_asset_version_path
    ON package_asset(version_id, path, id);

INSERT INTO system_setting (key, value) VALUES
    ('upload.max_package_size_mb', '500'),
    ('upload.max_unpacked_size_mb', '2048'),
    ('upload.max_file_count', '20000'),
    ('upload.max_single_file_size_mb', '200'),
    ('upload.max_path_length', '512'),
    ('cleanup.soft_delete_retention_days', '30'),
    ('task.poll_interval_seconds', '3'),
    ('task.thread_pool_size', '2'),
    ('preview.ticket_ttl_seconds', '60'),
    ('preview.session_ttl_seconds', '1800');

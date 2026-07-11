package com.kbpack.search;

import com.kbpack.common.config.KbpackProperties;
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Config;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.model.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration
public class MeilisearchConfig {

    private static final Logger log = LoggerFactory.getLogger(MeilisearchConfig.class);

    @Bean
    Client meilisearchClient(KbpackProperties properties) {
        var meili = properties.getSearch().getMeilisearch();
        return new Client(new Config(meili.getHost(), meili.getApiKey()));
    }

    @Bean
    @Order(30)
    ApplicationRunner meilisearchIndexInitializer(Client client, KbpackProperties properties) {
        return args -> {
            String uid = properties.getSearch().getMeilisearch().getIndexUid();
            try {
                try {
                    client.createIndex(uid, "id");
                } catch (Exception ignored) {
                    // already exists
                }
                Index index = client.index(uid);
                Settings settings = new Settings();
                settings.setSearchableAttributes(new String[]{
                        "content_enhanced", "heading", "document_title", "package_title", "tags"
                });
                settings.setFilterableAttributes(new String[]{
                        "package_id", "version_id", "document_id", "status", "source_type", "tags", "collection_ids",
                        "owner_id", "visibility"
                });
                settings.setDisplayedAttributes(new String[]{
                        "id", "package_id", "version_id", "document_id", "document_title", "heading",
                        "content", "package_title", "tags", "anchor", "updated_at", "preview_url"
                });
                index.updateSettings(settings);
                log.info("Meilisearch index '{}' settings applied", uid);
            } catch (Exception e) {
                log.error("Failed to initialize Meilisearch index {}: {}", uid, e.getMessage());
            }
        };
    }
}

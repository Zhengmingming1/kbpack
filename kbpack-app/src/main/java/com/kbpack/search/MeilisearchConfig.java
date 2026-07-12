package com.kbpack.search;

import com.kbpack.common.config.KbpackProperties;
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Config;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.model.Settings;
import com.meilisearch.sdk.model.Task;
import com.meilisearch.sdk.model.TaskInfo;
import com.meilisearch.sdk.model.TaskStatus;
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
                Index index;
                try {
                    index = client.getIndex(uid);
                } catch (Exception missing) {
                    TaskInfo creation = client.createIndex(uid, "id");
                    waitForSuccessfulTask(client, client.index(uid), creation.getTaskUid(), properties);
                    index = client.getIndex(uid);
                }
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
                TaskInfo settingsUpdate = index.updateSettings(settings);
                waitForSuccessfulTask(client, index, settingsUpdate.getTaskUid(), properties);
                log.info("Meilisearch index '{}' settings applied", uid);
            } catch (Exception e) {
                log.error("Failed to initialize Meilisearch index {}: {}", uid, e.getMessage());
            }
        };
    }

    private static int taskTimeoutMillis(KbpackProperties properties) {
        long seconds = Math.min(Integer.MAX_VALUE / 1_000L,
                Math.max(30, properties.getSearch().getMeilisearch().getTaskTimeoutSeconds()));
        return Math.toIntExact(seconds * 1_000);
    }

    private static void waitForSuccessfulTask(
            Client client,
            Index index,
            int taskUid,
            KbpackProperties properties
    ) throws Exception {
        index.waitForTask(taskUid, taskTimeoutMillis(properties), 50);
        Task task = client.getTask(taskUid);
        if (task.getStatus() != TaskStatus.SUCCEEDED) {
            String message = task.getError() == null
                    ? String.valueOf(task.getStatus()) : task.getError().getMessage();
            throw new IllegalStateException("Meilisearch task " + taskUid + " did not succeed: " + message);
        }
    }
}

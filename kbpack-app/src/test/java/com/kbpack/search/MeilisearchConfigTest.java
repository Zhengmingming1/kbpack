package com.kbpack.search;

import com.kbpack.common.config.KbpackProperties;
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.model.Settings;
import com.meilisearch.sdk.model.Task;
import com.meilisearch.sdk.model.TaskInfo;
import com.meilisearch.sdk.model.TaskStatus;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MeilisearchConfigTest {

    @Test
    void creationAndSettingsUseConfiguredTimeoutAndVerifyTaskSuccess() throws Exception {
        Client client = mock(Client.class);
        Index index = mock(Index.class);
        TaskInfo creation = mock(TaskInfo.class);
        TaskInfo settingsUpdate = mock(TaskInfo.class);
        Task succeeded = mock(Task.class);
        KbpackProperties properties = new KbpackProperties();
        properties.getSearch().getMeilisearch().setTaskTimeoutSeconds(120);
        String uid = properties.getSearch().getMeilisearch().getIndexUid();
        when(client.getIndex(uid)).thenThrow(new RuntimeException("missing")).thenReturn(index);
        when(client.createIndex(uid, "id")).thenReturn(creation);
        when(creation.getTaskUid()).thenReturn(11);
        when(client.index(uid)).thenReturn(index);
        when(client.getTask(11)).thenReturn(succeeded);
        when(index.updateSettings(any(Settings.class))).thenReturn(settingsUpdate);
        when(settingsUpdate.getTaskUid()).thenReturn(22);
        when(client.getTask(22)).thenReturn(succeeded);
        when(succeeded.getStatus()).thenReturn(TaskStatus.SUCCEEDED);

        new MeilisearchConfig().meilisearchIndexInitializer(client, properties).run(null);

        verify(index).waitForTask(11, 120_000, 50);
        verify(client).getTask(11);
        verify(index).waitForTask(22, 120_000, 50);
        verify(client).getTask(22);
    }
}

package com.kbpack.parser;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExtractedDocumentMetadataTest {

    @Test
    void computesPersistedDiffMetadataFromUtf8Content() {
        ExtractedDocument document = new ExtractedDocument();
        document.setContent("abc😀");

        document.onCreate();

        assertThat(document.getContentLength()).isEqualTo(4);
        assertThat(document.getContentHash()).isEqualTo(
                "90e58f5f0fff026a22b66f12fffde07ffaa76072ae4358d257f601df8f8d6bc4"
        );
    }

    @Test
    void refreshesDiffMetadataWhenContentChanges() {
        ExtractedDocument document = new ExtractedDocument();
        document.setContent("before");
        document.onCreate();

        document.setContent("after");
        document.onUpdate();

        assertThat(document.getContentLength()).isEqualTo(5);
        assertThat(document.getContentHash()).isEqualTo(
                "f39592393ef0859cb196a52693d2cea00fb2df784b3c04ae54aa7cadb8e562f8"
        );
    }
}

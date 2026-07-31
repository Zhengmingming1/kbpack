package com.kbpack.pkg;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UploadServiceContentTypeTest {

    @Test
    void mapsBrowserSensitivePreviewAssetTypes() {
        assertThat(UploadService.contentType("assets/app.cjs")).isEqualTo("text/javascript; charset=utf-8");
        assertThat(UploadService.contentType("assets/app.wasm")).isEqualTo("application/wasm");
        assertThat(UploadService.contentType("assets/font.woff2")).isEqualTo("font/woff2");
        assertThat(UploadService.contentType("assets/font.ttf")).isEqualTo("font/ttf");
        assertThat(UploadService.contentType("assets/icon.avif")).isEqualTo("image/avif");
        assertThat(UploadService.contentType("assets/video.mp4")).isEqualTo("video/mp4");
        assertThat(UploadService.contentType("assets/audio.mp3")).isEqualTo("audio/mpeg");
        assertThat(UploadService.contentType("manifest.webmanifest")).isEqualTo("application/manifest+json");
    }

    @Test
    void retainsOctetStreamFallbackForUnknownFiles() {
        assertThat(UploadService.contentType("assets/data.bin")).isEqualTo("application/octet-stream");
    }
}

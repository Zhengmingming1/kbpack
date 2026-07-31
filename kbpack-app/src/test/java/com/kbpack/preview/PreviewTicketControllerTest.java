package com.kbpack.preview;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PreviewTicketControllerTest {

    @Test
    void returnsRelativeUrlWhenMainAndPreviewOriginsMatch() {
        String url = PreviewTicketController.buildPreviewUrl(
                "https://kb.example.com/",
                "https://kb.example.com",
                "pkg_1",
                "ver_1",
                "docs/index file.html",
                "ticket.value");

        assertThat(url).isEqualTo(
                "/p/pkg_1/v/ver_1/docs/index%20file.html?ticket=ticket.value");
    }

    @Test
    void keepsAbsoluteUrlForIsolatedPreviewOrigin() {
        String url = PreviewTicketController.buildPreviewUrl(
                "https://kb.example.com",
                "https://preview.example.com/",
                "pkg_1",
                "ver_1",
                "index.html",
                "ticket.value");

        assertThat(url).isEqualTo(
                "https://preview.example.com/p/pkg_1/v/ver_1/index.html?ticket=ticket.value");
    }
}

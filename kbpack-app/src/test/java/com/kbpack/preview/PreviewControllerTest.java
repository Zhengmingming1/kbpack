package com.kbpack.preview;

import com.kbpack.common.config.KbpackProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class PreviewControllerTest {

    @Test
    void usesSelfAsFrameAncestorForSingleOriginPreview() {
        KbpackProperties properties = new KbpackProperties();
        properties.setAppBaseUrl("https://kb.example.com");
        properties.setPreviewBaseUrl("https://kb.example.com/");

        PreviewController controller = new PreviewController(null, null, null, null, null, properties);

        assertThat(controller.frameAncestor()).isEqualTo("'self'");
    }

    @Test
    void keepsMainOriginAsFrameAncestorForIsolatedPreview() {
        KbpackProperties properties = new KbpackProperties();
        properties.setAppBaseUrl("https://kb.example.com");
        properties.setPreviewBaseUrl("https://preview.example.com");

        PreviewController controller = new PreviewController(null, null, null, null, null, properties);

        assertThat(controller.frameAncestor()).isEqualTo("https://kb.example.com");
    }

    @Test
    void limitsPreviewConnectionsToTheCurrentVersionPath() {
        KbpackProperties properties = new KbpackProperties();
        properties.setPreviewBaseUrl("https://kb.example.com");
        PreviewController controller = new PreviewController(null, null, null, null, null, properties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("192.0.2.10");
        request.setServerPort(28080);

        assertThat(controller.previewContentSources(request, "pkg_1", "ver_1"))
                .isEqualTo("https://kb.example.com/p/pkg_1/v/ver_1/ "
                        + "http://192.0.2.10:28080/p/pkg_1/v/ver_1/");
    }
}

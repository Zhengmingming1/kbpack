package com.kbpack.search;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchIndexServiceTest {

    @Test
    void duplicatesUppercaseAcronymsForTokenization() {
        String enhanced = SearchIndexService.enhance("BFM API", "");

        assertThat(enhanced).contains("BFM API BFM API");
    }

    @Test
    void splitsCamelCaseIdentifiers() {
        String enhanced = SearchIndexService.enhance("OrderCreateController", null);

        assertThat(enhanced).contains("Order Create Controller");
    }

    @Test
    void addsExtensionlessPathTokens() {
        String enhanced = SearchIndexService.enhance(
                "",
                "assets/chapters/order_create-guide.md"
        );

        assertThat(enhanced.trim()).isEqualTo("assets chapters order create guide");
    }
}

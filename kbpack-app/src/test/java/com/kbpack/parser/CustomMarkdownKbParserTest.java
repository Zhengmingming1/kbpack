package com.kbpack.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kbpack.pkg.PackageVersion;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CustomMarkdownKbParserTest {
    private final CustomMarkdownKbParser parser = new CustomMarkdownKbParser(new ObjectMapper());

    @Test
    void parsesKnowledgeBaseRelativeToWrappedHtmlEntry() {
        PackageVersion version = new PackageVersion();
        version.setEntryFile("kb/index.html");
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("kb/index.html", bytes("<html><head><title>Wrapped guide</title></head></html>"));
        files.put("kb/assets/content.js", bytes("['assets/chapters/second.md','assets/chapters/first.md']"));
        files.put("kb/assets/chapters/first.md", bytes("# First\nFirst body"));
        files.put("kb/assets/chapters/second.md", bytes("# Second\nSecond body"));
        files.put("kb/assets/chapters/_meta.json", bytes("{\"score\": 91}"));
        files.put("assets/chapters/outside.md", bytes("# Outside\nMust not be parsed"));
        files.put("__MACOSX/kb/._index.html", new byte[]{0, 5, 22, 7});
        PackageContext context = new PackageContext(null, version, files);

        assertThat(parser.canHandle(context)).isTrue();

        ParseResult result = parser.parse(context);
        assertThat(result.detectedTitle()).isEqualTo("Wrapped guide");
        assertThat(result.documents()).extracting(ParsedDocument::sourcePath)
                .containsExactly("kb/assets/chapters/second.md", "kb/assets/chapters/first.md");
        assertThat(result.documents()).extracting(ParsedDocument::title)
                .containsExactly("Second", "First");
        assertThat(result.qualityMeta()).containsEntry("score", 91);
    }

    @Test
    void keepsSupportingRootLevelPackages() {
        PackageVersion version = new PackageVersion();
        version.setEntryFile("index.html");
        PackageContext context = new PackageContext(null, version, Map.of(
                "index.html", bytes("<html><head><title>Root guide</title></head></html>"),
                "assets/chapters/intro.md", bytes("# Intro\nRoot body")
        ));

        assertThat(parser.canHandle(context)).isTrue();
        assertThat(parser.parse(context).documents()).singleElement().satisfies(document -> {
            assertThat(document.sourcePath()).isEqualTo("assets/chapters/intro.md");
            assertThat(document.title()).isEqualTo("Intro");
        });
    }

    @Test
    void doesNotClaimWrappedPackageWithChaptersOutsideEntryRoot() {
        PackageVersion version = new PackageVersion();
        version.setEntryFile("kb/index.html");
        PackageContext context = new PackageContext(null, version, Map.of(
                "kb/index.html", bytes("<html><head><title>Wrapped guide</title></head></html>"),
                "assets/chapters/outside.md", bytes("# Outside\nUnrelated content")
        ));

        assertThat(parser.canHandle(context)).isFalse();
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}

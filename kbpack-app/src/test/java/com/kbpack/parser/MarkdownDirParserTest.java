package com.kbpack.parser;

import com.kbpack.pkg.PackageVersion;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownDirParserTest {
    private final MarkdownDirParser parser = new MarkdownDirParser();

    @Test
    void recognizesLongMarkdownExtension() {
        PackageContext context = new PackageContext(null, null, Map.of(
                "guides/readme.markdown", "# Long extension\nContent".getBytes(StandardCharsets.UTF_8)));

        assertThat(parser.canHandle(context)).isTrue();
        ParsedDocument document = parser.parse(context).documents().getFirst();
        assertThat(document.sourcePath()).isEqualTo("guides/readme.markdown");
        assertThat(document.title()).isEqualTo("Long extension");
    }

    @Test
    void leavesAuxiliaryMarkdownToHtmlParserWhenEntryIsHtml() {
        PackageVersion version = new PackageVersion();
        version.setEntryFile("index.html");
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("index.html", "<html><body><h1>HTML entry</h1></body></html>"
                .getBytes(StandardCharsets.UTF_8));
        files.put("README.markdown", "# Auxiliary notes".getBytes(StandardCharsets.UTF_8));
        PackageContext context = new PackageContext(null, version, files);

        assertThat(parser.canHandle(context)).isFalse();

        ParseResult result = new ParserChain(List.of(parser, new HtmlParser())).parse(context);
        assertThat(result.documents()).singleElement().satisfies(document -> {
            assertThat(document.sourcePath()).isEqualTo("index.html");
            assertThat(document.docType()).isEqualTo(ExtractedDocument.DocType.html);
        });
    }
}

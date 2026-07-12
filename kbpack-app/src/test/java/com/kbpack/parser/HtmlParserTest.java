package com.kbpack.parser;

import com.kbpack.pkg.PackageVersion;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlParserTest {
    private final HtmlParser parser = new HtmlParser();

    @Test
    void convertsCleanHtmlBodyToStructuredMarkdownAndKeepsRawContent() {
        String raw = """
                <!doctype html>
                <html>
                <head>
                  <title>Readable guide</title>
                  <style>.hidden { display: none; }</style>
                  <script>alert('not content');</script>
                </head>
                <body>
                  <h1 id="original-id">Overview</h1>
                  <a href="#original-id">Jump to overview</a>
                  <p>Hello <strong>world</strong>. Read the <a href="https://example.com/docs">docs</a>.</p>
                  <pre><code class="language-markdown"># Not a document heading</code></pre>
                  <h2>Details <em>inside</em></h2>
                  <div>First block</div><div>Second block</div>
                  <ul><li>First item</li><li>Second item</li></ul>
                  <blockquote><p>Important note</p></blockquote>
                  <pre><code class="language-java">if (left &lt; right) {
                    return left;
                  }</code></pre>
                  <table>
                    <thead><tr><th>Name</th><th>Value</th></tr></thead>
                    <tbody><tr><td>Alpha</td><td>One</td></tr></tbody>
                  </table>
                  <img src="images/diagram.png" alt="Diagram" title="Flow">
                  <a href="../参考 文档/page (1).html?q=中文 值#章节 1">Local guide</a>
                  <img src="images/流程 图%20v1.png" alt="Local diagram">
                  <noscript>not content</noscript>
                  <template>not content</template>
                </body>
                </html>
                """;
        PackageVersion version = new PackageVersion();
        version.setEntryFile("guide.html");
        PackageContext context = new PackageContext(null, version, new LinkedHashMap<>(Map.of(
                "guide.html", raw.getBytes(StandardCharsets.UTF_8))));

        ParsedDocument document = parser.parse(context).documents().getFirst();

        assertThat(document.title()).isEqualTo("Readable guide");
        assertThat(document.content())
                .contains("# Overview")
                .contains("[Jump to overview](#overview)")
                .contains("Hello **world**")
                .contains("Read the [docs](https://example.com/docs).")
                .contains("## Details *inside*")
                .contains("First block\n\nSecond block")
                .contains("- First item")
                .contains("> Important note")
                .contains("```java")
                .contains("if (left < right)")
                .contains("| Name")
                .contains("| Alpha")
                .contains("![Diagram](images/diagram.png \"Flow\")")
                .contains("[Local guide](../%E5%8F%82%E8%80%83%20%E6%96%87%E6%A1%A3/page%20%281%29.html?q=%E4%B8%AD%E6%96%87%20%E5%80%BC#%E7%AB%A0%E8%8A%82%201)")
                .contains("![Local diagram](images/%E6%B5%81%E7%A8%8B%20%E5%9B%BE%20v1.png)")
                .doesNotContain("alert")
                .doesNotContain("not content")
                .doesNotContain("{#original-id}");
        assertThat(document.rawContent()).isEqualTo(raw);
        assertThat(document.headingTree()).containsExactly(
                Map.of("level", 1, "text", "Overview", "anchor", "overview"),
                Map.of("level", 2, "text", "Details inside", "anchor", "details-inside"));
    }

    @Test
    void disambiguatesDuplicateHeadingAnchorsFromConvertedMarkdown() {
        String raw = "<html><body><h2>Repeat</h2><h2>Repeat</h2></body></html>";
        PackageVersion version = new PackageVersion();
        version.setEntryFile("index.html");
        PackageContext context = new PackageContext(null, version, Map.of(
                "index.html", raw.getBytes(StandardCharsets.UTF_8)));

        List<Map<String, Object>> headings = parser.parse(context).documents().getFirst().headingTree();

        assertThat(headings).extracting(item -> item.get("anchor"))
                .containsExactly("repeat", "repeat-2");
    }

    @Test
    void rewritesHeadingAndLegacyAnchorAliasesToGeneratedHeadingAnchors() {
        String raw = """
                <html><body>
                  <nav>
                    <a href="#self-id">Self id</a>
                    <a href="#self-name">Self name</a>
                    <a href="#inside-id">Inside id</a>
                    <a href="#inside-name">Inside name</a>
                    <a href="#before-id">Before id</a>
                    <a href="#before-name">Before name</a>
                  </nav>
                  <h2 id="self-id" name="self-name">Self heading</h2>
                  <h2><a id="inside-id" name="inside-name"></a>Inside heading</h2>
                  <a id="before-id"></a>
                  <a name="before-name"></a>
                  <h2>Previous heading</h2>
                </body></html>
                """;
        PackageVersion version = new PackageVersion();
        version.setEntryFile("index.html");
        PackageContext context = new PackageContext(null, version, Map.of(
                "index.html", raw.getBytes(StandardCharsets.UTF_8)));

        String content = parser.parse(context).documents().getFirst().content();

        assertThat(content)
                .contains("[Self id](#self-heading)")
                .contains("[Self name](#self-heading)")
                .contains("[Inside id](#inside-heading)")
                .contains("[Inside name](#inside-heading)")
                .contains("[Before id](#previous-heading)")
                .contains("[Before name](#previous-heading)");
    }
}

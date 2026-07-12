package com.kbpack.parser;

import com.vladsch.flexmark.html2md.converter.ExtensionConversion;
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class HtmlParser implements Parser {
    private static final FlexmarkHtmlConverter HTML_TO_MARKDOWN = FlexmarkHtmlConverter.builder(
            new MutableDataSet()
                    .set(FlexmarkHtmlConverter.SETEXT_HEADINGS, false)
                    .set(FlexmarkHtmlConverter.OUTPUT_ATTRIBUTES_ID, false)
                    .set(FlexmarkHtmlConverter.RENDER_COMMENTS, false)
                    .set(FlexmarkHtmlConverter.OUTPUT_UNKNOWN_TAGS, false)
                    .set(FlexmarkHtmlConverter.DIV_AS_PARAGRAPH, true)
                    .set(FlexmarkHtmlConverter.UNORDERED_LIST_DELIMITER, '-')
                    .set(FlexmarkHtmlConverter.EXT_TABLES, ExtensionConversion.MARKDOWN))
            .build();

    @Override
    public boolean canHandle(PackageContext context) {
        return context.files().keySet().stream().anyMatch(path -> path.toLowerCase(Locale.ROOT).matches(".*\\.html?"));
    }

    @Override
    public ParseResult parse(PackageContext context) {
        List<String> paths = context.files().keySet().stream()
                .filter(path -> path.toLowerCase(Locale.ROOT).matches(".*\\.html?"))
                .sorted(Comparator.comparing(path -> !path.equals(context.version().getEntryFile())))
                .toList();
        List<ParsedDocument> documents = new ArrayList<>();
        int order = 1;
        for (String path : paths) {
            String raw = new String(context.files().get(path), StandardCharsets.UTF_8);
            Document html = Jsoup.parse(raw);
            html.select("script,style,noscript,template").remove();
            rewriteHeadingFragments(html);
            normalizeMarkdownUrls(html);
            String title = !html.title().isBlank() ? html.title()
                    : html.selectFirst("h1") != null ? html.selectFirst("h1").text()
                    : path.substring(path.lastIndexOf('/') + 1);
            String content = HTML_TO_MARKDOWN.convert(html.body()).trim();
            documents.add(new ParsedDocument(path, title, ExtractedDocument.DocType.html, order++,
                    content, raw, MarkdownTools.headings(content)));
        }
        return new ParseResult(documents.getFirst().title(), documents);
    }

    private static void rewriteHeadingFragments(Document html) {
        Map<String, Integer> seen = new LinkedHashMap<>();
        Map<String, String> originalAnchors = new LinkedHashMap<>();
        for (Element heading : html.select("h1,h2,h3,h4,h5,h6")) {
            String base = MarkdownTools.slug(heading.text());
            int count = seen.merge(base, 1, Integer::sum);
            String anchor = count == 1 ? base : base + "-" + count;
            heading.getAllElements().forEach(element -> rememberAnchorAliases(originalAnchors, element, anchor));

            Element previous = heading.previousElementSibling();
            while (isLegacyAnchor(previous)) {
                rememberAnchorAliases(originalAnchors, previous, anchor);
                previous = previous.previousElementSibling();
            }
        }
        html.select("a[href^=#]").forEach(link -> {
            String href = link.attr("href");
            String rewritten = originalAnchors.get(href.substring(1));
            if (rewritten != null) link.attr("href", "#" + rewritten);
        });
    }

    private static boolean isLegacyAnchor(Element element) {
        return element != null && element.tagName().equals("a")
                && (!element.id().isBlank() || !element.attr("name").isBlank());
    }

    private static void rememberAnchorAliases(Map<String, String> anchors, Element element, String target) {
        if (!element.id().isBlank()) anchors.putIfAbsent(element.id(), target);
        String name = element.attr("name");
        if (!name.isBlank()) anchors.putIfAbsent(name, target);
    }

    private static void normalizeMarkdownUrls(Document html) {
        html.select("a[href]").forEach(element ->
                element.attr("href", markdownSafeUrl(element.attr("href"))));
        html.select("img[src]").forEach(element ->
                element.attr("src", markdownSafeUrl(element.attr("src"))));
    }

    private static String markdownSafeUrl(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length();) {
            char current = value.charAt(index);
            if (current == '%' && index + 2 < value.length()
                    && isHex(value.charAt(index + 1)) && isHex(value.charAt(index + 2))) {
                result.append(value, index, index + 3);
                index += 3;
                continue;
            }
            int codePoint = value.codePointAt(index);
            if (codePoint == '\\') {
                result.append('/');
            } else if (isMarkdownUrlCharacter(codePoint)) {
                result.appendCodePoint(codePoint);
            } else {
                byte[] bytes = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8);
                for (byte encoded : bytes) {
                    result.append('%');
                    String hex = Integer.toHexString(encoded & 0xff).toUpperCase(Locale.ROOT);
                    if (hex.length() == 1) result.append('0');
                    result.append(hex);
                }
            }
            index += Character.charCount(codePoint);
        }
        return result.toString();
    }

    private static boolean isMarkdownUrlCharacter(int codePoint) {
        return codePoint >= 'a' && codePoint <= 'z'
                || codePoint >= 'A' && codePoint <= 'Z'
                || codePoint >= '0' && codePoint <= '9'
                || "-._~:/?#@!$&'*+,;=".indexOf(codePoint) >= 0;
    }

    private static boolean isHex(char value) {
        return value >= '0' && value <= '9'
                || value >= 'a' && value <= 'f'
                || value >= 'A' && value <= 'F';
    }

    @Override
    public int priority() { return 6; }
}

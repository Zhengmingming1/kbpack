package com.kbpack.parser;

import com.vladsch.flexmark.ast.Heading;
import com.vladsch.flexmark.util.ast.TextCollectingVisitor;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class MarkdownTools {
    private static final com.vladsch.flexmark.parser.Parser MARKDOWN_PARSER =
            com.vladsch.flexmark.parser.Parser.builder().build();

    private MarkdownTools() {}

    static boolean isMarkdownPath(String path) {
        String normalized = path == null ? "" : path.toLowerCase(Locale.ROOT);
        return normalized.endsWith(".md") || normalized.endsWith(".markdown");
    }

    static String stripFrontMatter(String markdown) {
        String normalized = markdown == null ? "" : markdown.replace("\r\n", "\n");
        if (!normalized.startsWith("---\n")) return normalized;
        int end = normalized.indexOf("\n---\n", 4);
        return end < 0 ? normalized : normalized.substring(end + 5);
    }

    static String title(String markdown, String fallback) {
        List<HeadingInfo> headings = headingInfos(stripFrontMatter(markdown));
        return headings.isEmpty() ? fallback : headings.getFirst().text();
    }

    static List<Map<String, Object>> headings(String markdown) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (HeadingInfo heading : headingInfos(markdown)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("level", heading.level());
            item.put("text", heading.text());
            item.put("anchor", heading.anchor());
            result.add(item);
        }
        return result;
    }

    static List<HeadingInfo> headingInfos(String markdown) {
        List<HeadingInfo> result = new ArrayList<>();
        Map<String, Integer> seen = new LinkedHashMap<>();
        TextCollectingVisitor textCollector = new TextCollectingVisitor();
        for (var node : MARKDOWN_PARSER.parse(markdown == null ? "" : markdown).getDescendants()) {
            if (!(node instanceof Heading heading)) continue;
            String text = textCollector.collectAndGetText(heading).trim();
            String base = slug(text);
            int count = seen.merge(base, 1, Integer::sum);
            result.add(new HeadingInfo(
                    heading.getLevel(),
                    text,
                    count == 1 ? base : base + "-" + count,
                    heading.getStartOffset(),
                    heading.getEndOffset()
            ));
        }
        return result;
    }

    record HeadingInfo(int level, String text, String anchor, int startOffset, int endOffset) {}

    static String slug(String input) {
        String value = Normalizer.normalize(input == null ? "section" : input, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "-")
                .replaceAll("[^a-z0-9\\u4e00-\\u9fff-]", "")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        return value.isBlank() ? "section" : value;
    }
}

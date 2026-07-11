package com.kbpack.parser;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MarkdownTools {
    private static final Pattern HEADING = Pattern.compile("(?m)^(#{1,6})\\s+(.+?)\\s*$");

    private MarkdownTools() {}

    static String stripFrontMatter(String markdown) {
        String normalized = markdown == null ? "" : markdown.replace("\r\n", "\n");
        if (!normalized.startsWith("---\n")) return normalized;
        int end = normalized.indexOf("\n---\n", 4);
        return end < 0 ? normalized : normalized.substring(end + 5);
    }

    static String title(String markdown, String fallback) {
        Matcher matcher = HEADING.matcher(stripFrontMatter(markdown));
        return matcher.find() ? matcher.group(2).replaceAll("\\s+#+$", "").trim() : fallback;
    }

    static List<Map<String, Object>> headings(String markdown) {
        List<Map<String, Object>> result = new ArrayList<>();
        Map<String, Integer> seen = new LinkedHashMap<>();
        Matcher matcher = HEADING.matcher(markdown == null ? "" : markdown);
        while (matcher.find()) {
            String text = matcher.group(2).replaceAll("\\s+#+$", "").trim();
            String base = slug(text);
            int count = seen.merge(base, 1, Integer::sum);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("level", matcher.group(1).length());
            item.put("text", text);
            item.put("anchor", count == 1 ? base : base + "-" + count);
            result.add(item);
        }
        return result;
    }

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

package com.kbpack.parser;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CustomMarkdownKbParser implements Parser {
    private static final Pattern QUOTED_MARKDOWN = Pattern.compile(
            "['\"]([^'\"]+\\.(?:md|markdown))['\"]", Pattern.CASE_INSENSITIVE);
    private final ObjectMapper objectMapper;

    public CustomMarkdownKbParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean canHandle(PackageContext context) {
        String root = entryRoot(context);
        return context.has(root + "index.html") && context.files().keySet().stream()
                .anyMatch(path -> isChapter(path, root));
    }

    @Override
    public ParseResult parse(PackageContext context) {
        String root = entryRoot(context);
        List<String> markdownFiles = context.files().keySet().stream()
                .filter(path -> isChapter(path, root))
                .sorted(Comparator.naturalOrder())
                .toList();
        List<String> ordered = orderFromContentJs(context, root, markdownFiles);
        List<ParsedDocument> documents = new ArrayList<>();
        int index = 1;
        for (String path : ordered) {
            String raw = new String(context.files().get(path), StandardCharsets.UTF_8);
            String content = MarkdownTools.stripFrontMatter(raw);
            documents.add(new ParsedDocument(path, MarkdownTools.title(raw, filenameTitle(path)),
                    ExtractedDocument.DocType.markdown, index++, content, raw, MarkdownTools.headings(content)));
        }
        String indexHtml = context.text(root + "index.html");
        String detectedTitle = indexHtml == null ? null : Jsoup.parse(indexHtml).title();
        return new ParseResult(detectedTitle, documents, readQualityMeta(context, root));
    }

    @Override
    public int priority() { return 1; }

    private List<String> orderFromContentJs(PackageContext context, String root, List<String> files) {
        String script = context.text(root + "assets/content.js");
        if (script == null) return files;
        List<String> ordered = new ArrayList<>();
        Matcher matcher = QUOTED_MARKDOWN.matcher(script);
        while (matcher.find()) {
            String candidate = matcher.group(1).replace('\\', '/');
            files.stream()
                    .filter(path -> path.equals(candidate) || path.endsWith("/" + candidate)
                            || path.endsWith("/" + candidate.substring(candidate.lastIndexOf('/') + 1)))
                    .findFirst()
                    .filter(path -> !ordered.contains(path))
                    .ifPresent(ordered::add);
        }
        files.stream().filter(path -> !ordered.contains(path)).forEach(ordered::add);
        return ordered;
    }

    private Map<String, Object> readQualityMeta(PackageContext context, String root) {
        String json = context.text(root + "assets/chapters/_meta.json");
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String filenameTitle(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1)
                .replaceFirst("(?i)\\.(?:md|markdown)$", "");
        return name.replace('-', ' ').replace('_', ' ');
    }

    private static String entryRoot(PackageContext context) {
        if (context.version() == null || context.version().getEntryFile() == null) return "";
        String entry = context.version().getEntryFile().replace('\\', '/');
        int separator = entry.lastIndexOf('/');
        return separator < 0 ? "" : entry.substring(0, separator + 1);
    }

    private static boolean isChapter(String path, String root) {
        String relative = path;
        if (!root.isEmpty()) {
            if (path.length() < root.length()
                    || !path.regionMatches(true, 0, root, 0, root.length())) {
                return false;
            }
            relative = path.substring(root.length());
        }
        return relative.toLowerCase(Locale.ROOT).matches("assets/chapters/.+\\.(?:md|markdown)");
    }
}

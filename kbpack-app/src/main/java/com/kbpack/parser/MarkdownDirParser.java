package com.kbpack.parser;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Component
public class MarkdownDirParser implements Parser {
    @Override
    public boolean canHandle(PackageContext context) {
        return context.files().keySet().stream().anyMatch(path -> path.toLowerCase(Locale.ROOT).endsWith(".md"));
    }

    @Override
    public ParseResult parse(PackageContext context) {
        List<String> paths = context.files().keySet().stream()
                .filter(path -> path.toLowerCase(Locale.ROOT).endsWith(".md"))
                .sorted(Comparator.naturalOrder()).toList();
        List<ParsedDocument> documents = new ArrayList<>();
        int order = 1;
        for (String path : paths) {
            String raw = new String(context.files().get(path), StandardCharsets.UTF_8);
            String content = MarkdownTools.stripFrontMatter(raw);
            String fallback = path.substring(path.lastIndexOf('/') + 1).replaceFirst("(?i)\\.md$", "");
            documents.add(new ParsedDocument(path, MarkdownTools.title(raw, fallback),
                    ExtractedDocument.DocType.markdown, order++, content, raw, MarkdownTools.headings(content)));
        }
        return new ParseResult(documents.getFirst().title(), documents);
    }

    @Override
    public int priority() { return 4; }
}

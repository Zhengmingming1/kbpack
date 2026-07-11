package com.kbpack.parser;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Component
public class PlainTextParser implements Parser {
    @Override
    public boolean canHandle(PackageContext context) {
        return context.files().keySet().stream().anyMatch(PlainTextParser::supported);
    }

    @Override
    public ParseResult parse(PackageContext context) {
        List<String> paths = context.files().keySet().stream().filter(PlainTextParser::supported)
                .sorted(Comparator.naturalOrder()).toList();
        List<ParsedDocument> documents = new ArrayList<>();
        int order = 1;
        for (String path : paths) {
            String content = new String(context.files().get(path), StandardCharsets.UTF_8);
            String title = path.substring(path.lastIndexOf('/') + 1);
            documents.add(new ParsedDocument(path, title, ExtractedDocument.DocType.text, order++,
                    content, content, List.of()));
        }
        return new ParseResult(documents.getFirst().title(), documents);
    }

    @Override
    public int priority() { return 7; }

    private static boolean supported(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.matches(".*\\.(txt|json|csv|log|xml|ya?ml)$");
    }
}

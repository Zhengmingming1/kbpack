package com.kbpack.parser;

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
            String title = !html.title().isBlank() ? html.title()
                    : html.selectFirst("h1") != null ? html.selectFirst("h1").text()
                    : path.substring(path.lastIndexOf('/') + 1);
            List<Map<String, Object>> headings = new ArrayList<>();
            for (Element heading : html.select("h1,h2,h3,h4,h5,h6")) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("level", Integer.parseInt(heading.tagName().substring(1)));
                item.put("text", heading.text());
                item.put("anchor", heading.id().isBlank() ? MarkdownTools.slug(heading.text()) : heading.id());
                headings.add(item);
            }
            documents.add(new ParsedDocument(path, title, ExtractedDocument.DocType.html, order++,
                    html.body().text(), raw, headings));
        }
        return new ParseResult(documents.getFirst().title(), documents);
    }

    @Override
    public int priority() { return 6; }
}

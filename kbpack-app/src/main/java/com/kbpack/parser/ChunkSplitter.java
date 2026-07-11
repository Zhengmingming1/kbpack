package com.kbpack.parser;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ChunkSplitter {
    static final int TARGET_SIZE = 500;
    static final int OVERLAP = 50;
    static final int MIN_SIZE = 30;
    private static final Pattern HEADING = Pattern.compile("(?m)^#{1,6}\\s+(.+?)\\s*$");

    public record ChunkPart(String heading, String content, String anchor) {}

    public List<ChunkPart> split(ParsedDocument document) {
        String content = document.content() == null ? "" : document.content().trim();
        if (content.isBlank()) return List.of();
        List<Section> sections = sections(content, document.title());
        List<ChunkPart> chunks = new ArrayList<>();
        for (Section section : sections) {
            appendSection(chunks, section);
        }
        mergeSmallTail(chunks);
        return chunks;
    }

    private void appendSection(List<ChunkPart> chunks, Section section) {
        String body = section.content().trim();
        if (body.length() <= TARGET_SIZE) {
            chunks.add(new ChunkPart(section.heading(), body, section.anchor()));
            return;
        }
        String[] paragraphs = body.split("\\n\\s*\\n");
        StringBuilder current = new StringBuilder();
        for (String paragraph : paragraphs) {
            String value = paragraph.trim();
            if (value.isEmpty()) continue;
            if (value.length() > TARGET_SIZE) {
                flush(chunks, section, current);
                hardSplit(chunks, section, value);
            } else if (!current.isEmpty() && current.length() + value.length() + 2 > TARGET_SIZE) {
                String overlap = tail(current.toString(), OVERLAP);
                flush(chunks, section, current);
                String combined = overlap.isEmpty() ? value : overlap + "\n\n" + value;
                if (combined.length() > TARGET_SIZE) {
                    hardSplit(chunks, section, combined);
                } else {
                    current.append(combined);
                }
            } else {
                if (!current.isEmpty()) current.append("\n\n");
                current.append(value);
            }
        }
        flush(chunks, section, current);
    }

    private void hardSplit(List<ChunkPart> chunks, Section section, String value) {
        int start = 0;
        while (start < value.length()) {
            int end = Math.min(start + TARGET_SIZE, value.length());
            if (end < value.length()) {
                int sentence = Math.max(value.lastIndexOf('。', end), value.lastIndexOf('\n', end));
                if (sentence > start + MIN_SIZE) end = sentence + 1;
            }
            chunks.add(new ChunkPart(section.heading(), value.substring(start, end).trim(), section.anchor()));
            if (end == value.length()) break;
            start = Math.max(start + 1, end - OVERLAP);
        }
    }

    private void flush(List<ChunkPart> chunks, Section section, StringBuilder current) {
        if (current.isEmpty()) return;
        chunks.add(new ChunkPart(section.heading(), current.toString().trim(), section.anchor()));
        current.setLength(0);
    }

    private List<Section> sections(String content, String defaultHeading) {
        List<Section> result = new ArrayList<>();
        Matcher matcher = HEADING.matcher(content);
        int cursor = 0;
        String heading = defaultHeading;
        String anchor = MarkdownTools.slug(defaultHeading);
        while (matcher.find()) {
            if (matcher.start() > cursor) {
                String section = content.substring(cursor, matcher.start()).trim();
                if (!section.isBlank()) result.add(new Section(heading, anchor, section));
            }
            heading = matcher.group(1).replaceAll("\\s+#+$", "").trim();
            anchor = MarkdownTools.slug(heading);
            cursor = matcher.end();
        }
        if (cursor < content.length()) {
            String section = content.substring(cursor).trim();
            if (!section.isBlank()) result.add(new Section(heading, anchor, section));
        }
        if (result.isEmpty()) result.add(new Section(defaultHeading, anchor, content));
        return result;
    }

    private void mergeSmallTail(List<ChunkPart> chunks) {
        if (chunks.size() < 2) return;
        ChunkPart last = chunks.getLast();
        if (last.content().length() >= MIN_SIZE) return;
        ChunkPart previous = chunks.get(chunks.size() - 2);
        chunks.set(chunks.size() - 2, new ChunkPart(previous.heading(),
                previous.content() + "\n\n" + last.content(), previous.anchor()));
        chunks.removeLast();
    }

    private static String tail(String value, int length) {
        return value.substring(Math.max(0, value.length() - length)).trim();
    }

    private record Section(String heading, String anchor, String content) {}
}

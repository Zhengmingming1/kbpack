package com.kbpack.parser;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkSplitterTest {

    private final ChunkSplitter splitter = new ChunkSplitter();

    @Test
    void markdownHeadingTakesPriorityOverDocumentTitle() {
        List<ChunkSplitter.ChunkPart> chunks = splitter.split(document(
                "Document title",
                "# Preferred heading\nSection body"
        ));

        assertEquals(1, chunks.size());
        assertEquals("Preferred heading", chunks.getFirst().heading());
        assertEquals("preferred-heading", chunks.getFirst().anchor());
        assertEquals("Section body", chunks.getFirst().content());
    }

    @Test
    void documentTitleIsFallbackHeadingWhenMarkdownHasNoHeading() {
        List<ChunkSplitter.ChunkPart> chunks = splitter.split(document(
                "Document title",
                "Body without a Markdown heading"
        ));

        assertEquals(1, chunks.size());
        assertEquals("Document title", chunks.getFirst().heading());
        assertEquals("document-title", chunks.getFirst().anchor());
    }

    @Test
    void hardSplitRespectsMaximumSizeAndCarriesOverlap() {
        List<ChunkSplitter.ChunkPart> chunks = splitter.split(document("Large", randomText(1_100)));

        assertTrue(chunks.size() >= 3);
        assertTrue(chunks.stream().allMatch(chunk -> chunk.content().length() <= ChunkSplitter.TARGET_SIZE));
        for (int index = 1; index < chunks.size(); index++) {
            String previous = chunks.get(index - 1).content();
            String current = chunks.get(index).content();
            assertEquals(
                    previous.substring(previous.length() - ChunkSplitter.OVERLAP),
                    current.substring(0, ChunkSplitter.OVERLAP)
            );
        }
    }

    @Test
    void paragraphSplitNeverExceedsMaximumChunkSize() {
        String content = "a".repeat(480) + "\n\n" + "b".repeat(480);

        List<ChunkSplitter.ChunkPart> chunks = splitter.split(document("Paragraphs", content));

        assertFalse(chunks.isEmpty());
        assertTrue(chunks.stream().allMatch(chunk -> chunk.content().length() <= ChunkSplitter.TARGET_SIZE));
    }

    @Test
    void mergesFinalChunkSmallerThanMinimumIntoPreviousChunk() {
        String content = "# First\n" + "a".repeat(40) + "\n\n# Last\ntiny";

        List<ChunkSplitter.ChunkPart> chunks = splitter.split(document("Document", content));

        assertEquals(1, chunks.size());
        assertEquals("First", chunks.getFirst().heading());
        assertEquals("a".repeat(40) + "\n\ntiny", chunks.getFirst().content());
    }

    private static ParsedDocument document(String title, String content) {
        return new ParsedDocument(
                "chapter.md",
                title,
                ExtractedDocument.DocType.markdown,
                0,
                content,
                content,
                List.of()
        );
    }

    private static String randomText(int length) {
        Random random = new Random(42);
        StringBuilder value = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            value.append((char) ('a' + random.nextInt(26)));
        }
        return value.toString();
    }
}

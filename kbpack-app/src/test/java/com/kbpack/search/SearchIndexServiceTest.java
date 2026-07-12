package com.kbpack.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kbpack.common.config.KbpackProperties;
import com.kbpack.parser.ExtractedDocumentRepository;
import com.kbpack.parser.ExtractedDocument;
import com.kbpack.parser.SearchChunk;
import com.kbpack.parser.SearchChunkRepository;
import com.kbpack.pkg.KnowledgePackage;
import com.kbpack.pkg.KnowledgePackageRepository;
import com.kbpack.pkg.PackageCollectionRepository;
import com.kbpack.pkg.PackageTagRepository;
import com.kbpack.pkg.PackageVersionRepository;
import com.kbpack.pkg.PackageVersion;
import com.kbpack.pkg.TagRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SearchIndexServiceTest {

    @Test
    void duplicatesUppercaseAcronymsForTokenization() {
        String enhanced = SearchIndexService.enhance("BFM API", "");

        assertThat(enhanced).contains("BFM API BFM API");
    }

    @Test
    void splitsCamelCaseIdentifiers() {
        String enhanced = SearchIndexService.enhance("OrderCreateController", null);

        assertThat(enhanced).contains("Order Create Controller");
    }

    @Test
    void addsExtensionlessPathTokens() {
        String enhanced = SearchIndexService.enhance(
                "",
                "assets/chapters/order_create-guide.md"
        );

        assertThat(enhanced.trim()).isEqualTo("assets chapters order create guide");
    }

    @Test
    void reindexClearsAndAwaitsIndexEvenWhenDatabaseIsEmpty() throws Exception {
        List<String> requests = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            if ("DELETE".equals(exchange.getRequestMethod())) {
                respond(exchange, 202, "{\"taskUid\":1}");
            } else if ("/tasks/1".equals(exchange.getRequestURI().getPath())) {
                respond(exchange, 200, "{\"status\":\"succeeded\"}");
            } else {
                respond(exchange, 404, "{}");
            }
        });
        server.start();
        try {
            KbpackProperties properties = new KbpackProperties();
            properties.getSearch().getMeilisearch()
                    .setHost("http://127.0.0.1:" + server.getAddress().getPort());
            SearchIndexService service = new SearchIndexService(
                    properties,
                    new ObjectMapper(),
                    mock(SearchChunkRepository.class),
                    mock(ExtractedDocumentRepository.class),
                    mock(KnowledgePackageRepository.class),
                    mock(PackageTagRepository.class),
                    mock(TagRepository.class),
                    mock(PackageCollectionRepository.class),
                    mock(PackageVersionRepository.class)
            );

            service.reindexAll();

            assertThat(requests).containsExactly(
                    "DELETE /indexes/kb_chunks/documents",
                    "GET /tasks/1"
            );
        } finally {
            server.stop(0);
        }
    }

    @Test
    void reindexWaitsForClearBeforeAddingReplacementDocuments() throws Exception {
        AtomicBoolean clearCompleted = new AtomicBoolean();
        List<String> requests = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            requests.add(method + " " + path);
            if ("DELETE".equals(method)) {
                respond(exchange, 202, "{\"taskUid\":1}");
            } else if ("GET".equals(method) && "/tasks/1".equals(path)) {
                clearCompleted.set(true);
                respond(exchange, 200, "{\"status\":\"succeeded\"}");
            } else if ("POST".equals(method) && clearCompleted.get()) {
                respond(exchange, 202, "{\"taskUid\":2}");
            } else if ("GET".equals(method) && "/tasks/2".equals(path)) {
                respond(exchange, 200, "{\"status\":\"succeeded\"}");
            } else {
                respond(exchange, 409, "{}");
            }
        });
        server.start();
        try {
            KbpackProperties properties = new KbpackProperties();
            properties.getSearch().getMeilisearch()
                    .setHost("http://127.0.0.1:" + server.getAddress().getPort());
            SearchChunkRepository chunks = mock(SearchChunkRepository.class);
            ExtractedDocumentRepository documents = mock(ExtractedDocumentRepository.class);
            KnowledgePackageRepository packages = mock(KnowledgePackageRepository.class);
            PackageVersionRepository versions = mock(PackageVersionRepository.class);
            UUID packageId = UUID.randomUUID();
            UUID versionId = UUID.randomUUID();
            UUID documentId = UUID.randomUUID();
            KnowledgePackage pkg = mock(KnowledgePackage.class);
            when(pkg.getId()).thenReturn(packageId);
            when(pkg.getTitle()).thenReturn("Package");
            when(pkg.getStatus()).thenReturn(KnowledgePackage.Status.active);
            when(pkg.getSourceType()).thenReturn(KnowledgePackage.SourceType.manual);
            when(pkg.getOwnerId()).thenReturn(UUID.randomUUID());
            when(pkg.getVisibility()).thenReturn("team");
            when(pkg.getCreatedAt()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
            when(pkg.getUpdatedAt()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
            ExtractedDocument document = mock(ExtractedDocument.class);
            when(document.getId()).thenReturn(documentId);
            when(document.getSourcePath()).thenReturn("guide.md");
            when(document.getTitle()).thenReturn("Guide");
            SearchChunk chunk = mock(SearchChunk.class);
            when(chunk.getId()).thenReturn(UUID.randomUUID());
            when(chunk.getPackageId()).thenReturn(packageId);
            when(chunk.getVersionId()).thenReturn(versionId);
            when(chunk.getDocumentId()).thenReturn(documentId);
            when(chunk.getContent()).thenReturn("content");
            PackageVersion version = mock(PackageVersion.class);
            when(version.getEntryFile()).thenReturn("guide.md");
            when(documents.findAll()).thenReturn(List.of(document));
            when(packages.findAll()).thenReturn(List.of(pkg));
            when(chunks.findAll()).thenReturn(List.of(chunk));
            when(versions.findActiveById(versionId)).thenReturn(Optional.of(version));
            SearchIndexService service = new SearchIndexService(
                    properties, new ObjectMapper(), chunks, documents, packages,
                    mock(PackageTagRepository.class), mock(TagRepository.class),
                    mock(PackageCollectionRepository.class), versions);

            service.reindexAll();

            assertThat(requests).containsExactly(
                    "DELETE /indexes/kb_chunks/documents",
                    "GET /tasks/1",
                    "POST /indexes/kb_chunks/documents",
                    "GET /tasks/2"
            );
        } finally {
            server.stop(0);
        }
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String json)
            throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}

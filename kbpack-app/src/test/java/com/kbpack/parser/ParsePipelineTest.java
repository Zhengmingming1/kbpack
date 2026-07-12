package com.kbpack.parser;

import com.kbpack.common.config.KbpackProperties;
import com.kbpack.common.storage.ObjectStorageService;
import com.kbpack.pkg.KnowledgePackage;
import com.kbpack.pkg.KnowledgePackageRepository;
import com.kbpack.pkg.PackageAsset;
import com.kbpack.pkg.PackageAssetRepository;
import com.kbpack.pkg.PackageVersion;
import com.kbpack.pkg.PackageVersionRepository;
import com.kbpack.search.SearchIndexUpdateCoordinator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParsePipelineTest {

    @Mock private PackageVersionRepository versionRepository;
    @Mock private KnowledgePackageRepository packageRepository;
    @Mock private PackageAssetRepository assetRepository;
    @Mock private ExtractedDocumentRepository documentRepository;
    @Mock private SearchChunkRepository chunkRepository;
    @Mock private ObjectStorageService storage;
    @Mock private ParserChain parserChain;
    @Mock private SearchIndexUpdateCoordinator searchIndexUpdates;

    private ParsePipeline pipeline;

    @BeforeEach
    void setUp() {
        pipeline = new ParsePipeline(
                versionRepository,
                packageRepository,
                assetRepository,
                documentRepository,
                chunkRepository,
                storage,
                parserChain,
                new ChunkSplitter(),
                searchIndexUpdates,
                new KbpackProperties());
    }

    @Test
    void historicalReparseDoesNotChangePackageLevelState() {
        UUID packageId = UUID.randomUUID();
        UUID historicalVersionId = UUID.randomUUID();
        UUID currentVersionId = UUID.randomUUID();
        Map<String, Object> originalQuality = Map.of("quality_notes", List.of("original"));
        KnowledgePackage pkg = packageWith(packageId, currentVersionId, KnowledgePackage.Status.archived);
        pkg.setQualityMeta(originalQuality);

        runPipeline(pkg, versionWith(packageId, historicalVersionId));

        assertThat(pkg.getCurrentVersionId()).isEqualTo(currentVersionId);
        assertThat(pkg.getStatus()).isEqualTo(KnowledgePackage.Status.archived);
        assertThat(pkg.getQualityMeta()).isSameAs(originalQuality);
        InOrder locks = inOrder(packageRepository, versionRepository);
        locks.verify(packageRepository).findActiveByIdForUpdate(packageId);
        locks.verify(versionRepository).findActiveByIdAndPackageIdForUpdate(historicalVersionId, packageId);
        assertThat(pkg.getTitle()).isEqualTo("未命名知识包");
    }

    @Test
    void currentDraftBecomesActiveButArchivedPackageStaysArchived() {
        UUID packageId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        KnowledgePackage draft = packageWith(packageId, versionId, KnowledgePackage.Status.draft);

        runPipeline(draft, versionWith(packageId, versionId));

        assertThat(draft.getStatus()).isEqualTo(KnowledgePackage.Status.active);
        assertThat(draft.getTitle()).isEqualTo("Detected title");
        assertThat(draft.getQualityMeta()).containsEntry("score", 1);

        KnowledgePackage archived = packageWith(packageId, versionId, KnowledgePackage.Status.archived);
        runPipeline(archived, versionWith(packageId, versionId));

        assertThat(archived.getStatus()).isEqualTo(KnowledgePackage.Status.archived);
    }

    private void runPipeline(KnowledgePackage pkg, PackageVersion version) {
        PackageAsset asset = new PackageAsset();
        asset.setVersionId(version.getId());
        asset.setPath("index.md");
        asset.setRole(PackageAsset.Role.markdown);
        asset.setSize(7);
        when(versionRepository.findActiveById(version.getId())).thenReturn(Optional.of(version));
        when(packageRepository.findActiveByIdForUpdate(pkg.getId())).thenReturn(Optional.of(pkg));
        when(versionRepository.findActiveByIdAndPackageIdForUpdate(version.getId(), pkg.getId()))
                .thenReturn(Optional.of(version));
        when(assetRepository.findByVersionIdOrderByPathAsc(version.getId())).thenReturn(List.of(asset));
        when(storage.packagesBucket()).thenReturn("packages");
        when(storage.readBytes(anyString(), anyString(), anyLong()))
                .thenReturn("content".getBytes(StandardCharsets.UTF_8));
        ParsedDocument document = new ParsedDocument(
                "index.md", "Chapter", ExtractedDocument.DocType.markdown, 0,
                "Enough content for a searchable chunk in this parser test.", null, List.of());
        when(parserChain.parse(any())).thenReturn(new ParseResult(
                "Detected title", List.of(document), Map.of("score", 1)));
        when(documentRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        pipeline.process(version.getId());
    }

    private KnowledgePackage packageWith(
            UUID packageId,
            UUID currentVersionId,
            KnowledgePackage.Status status
    ) {
        KnowledgePackage pkg = new KnowledgePackage();
        pkg.setId(packageId);
        pkg.setCurrentVersionId(currentVersionId);
        pkg.setStatus(status);
        pkg.setTitle("未命名知识包");
        return pkg;
    }

    private PackageVersion versionWith(UUID packageId, UUID versionId) {
        PackageVersion version = new PackageVersion();
        version.setId(versionId);
        version.setPackageId(packageId);
        version.setEntryFile("index.md");
        version.setParseStatus(PackageVersion.ParseStatus.pending);
        return version;
    }
}

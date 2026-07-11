package com.kbpack.parser;

import com.kbpack.common.error.ApiException;
import com.kbpack.common.error.ErrorCode;
import com.kbpack.common.config.KbpackProperties;
import com.kbpack.common.id.IdPrefix;
import com.kbpack.common.storage.ObjectStorageService;
import com.kbpack.pkg.KnowledgePackage;
import com.kbpack.pkg.KnowledgePackageRepository;
import com.kbpack.pkg.PackageAsset;
import com.kbpack.pkg.PackageAssetRepository;
import com.kbpack.pkg.PackageVersion;
import com.kbpack.pkg.PackageVersionRepository;
import com.kbpack.search.SearchIndexService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ParsePipeline {
    private final PackageVersionRepository versionRepository;
    private final KnowledgePackageRepository packageRepository;
    private final PackageAssetRepository assetRepository;
    private final ExtractedDocumentRepository documentRepository;
    private final SearchChunkRepository chunkRepository;
    private final ObjectStorageService storage;
    private final ParserChain parserChain;
    private final ChunkSplitter splitter;
    private final SearchIndexService searchIndexService;
    private final KbpackProperties properties;

    public ParsePipeline(
            PackageVersionRepository versionRepository,
            KnowledgePackageRepository packageRepository,
            PackageAssetRepository assetRepository,
            ExtractedDocumentRepository documentRepository,
            SearchChunkRepository chunkRepository,
            ObjectStorageService storage,
            ParserChain parserChain,
            ChunkSplitter splitter,
            SearchIndexService searchIndexService,
            KbpackProperties properties
    ) {
        this.versionRepository = versionRepository;
        this.packageRepository = packageRepository;
        this.assetRepository = assetRepository;
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.storage = storage;
        this.parserChain = parserChain;
        this.splitter = splitter;
        this.searchIndexService = searchIndexService;
        this.properties = properties;
    }

    @Transactional
    public void process(UUID versionId) {
        PackageVersion version = versionRepository.findActiveById(versionId)
                .orElseThrow(() -> new ApiException(ErrorCode.VERSION_NOT_FOUND));
        KnowledgePackage pkg = packageRepository.findActiveById(version.getPackageId())
                .orElseThrow(() -> new ApiException(ErrorCode.PACKAGE_NOT_FOUND));
        version.setParseStatus(PackageVersion.ParseStatus.processing);
        version.setParseError(null);
        versionRepository.save(version);

        Map<String, byte[]> files = new LinkedHashMap<>();
        String packageId = IdPrefix.PACKAGE.format(pkg.getId());
        String externalVersionId = IdPrefix.VERSION.format(version.getId());
        long loadedBytes = 0;
        for (PackageAsset asset : assetRepository.findByVersionIdOrderByPathAsc(versionId)) {
            if (!isParseCandidate(asset)) continue;
            if (asset.getSize() > properties.getParser().getMaxTextFileBytes()) {
                if (asset.getPath().equals(version.getEntryFile())) {
                    throw new ApiException(ErrorCode.PARSE_FAILED, "入口文件过大，无法安全解析");
                }
                continue;
            }
            loadedBytes += asset.getSize();
            if (loadedBytes > properties.getParser().getMaxInMemoryBytes()) {
                throw new ApiException(ErrorCode.PARSE_FAILED, "可解析文本总量超过内存安全限额");
            }
            String key = packageId + "/" + externalVersionId + "/files/" + asset.getPath();
            files.put(asset.getPath(), storage.readBytes(storage.packagesBucket(), key, asset.getSize() + 1));
        }
        if (files.isEmpty()) {
            throw new ApiException(ErrorCode.PARSE_FAILED, "版本没有可解析文件");
        }

        ParseResult result = parserChain.parse(new PackageContext(pkg, version, files));
        if (result.documents().isEmpty()) {
            throw new ApiException(ErrorCode.PARSE_FAILED, "解析器未提取到任何文档");
        }
        chunkRepository.deleteByVersionId(versionId);
        documentRepository.deleteByVersionId(versionId);
        documentRepository.flush();

        for (ParsedDocument parsed : result.documents()) {
            ExtractedDocument document = new ExtractedDocument();
            document.setVersionId(versionId);
            document.setPackageId(pkg.getId());
            document.setSourcePath(parsed.sourcePath());
            document.setTitle(parsed.title());
            document.setDocType(parsed.docType());
            document.setOrderNo(parsed.orderNo());
            document.setContent(parsed.content());
            document.setRawContent(parsed.rawContent());
            document.setHeadingTree(parsed.headingTree());
            document.setWordCount(parsed.content() == null ? 0 : parsed.content().codePointCount(0, parsed.content().length()));
            documentRepository.saveAndFlush(document);

            int chunkIndex = 0;
            for (ChunkSplitter.ChunkPart part : splitter.split(parsed)) {
                SearchChunk chunk = new SearchChunk();
                chunk.setDocumentId(document.getId());
                chunk.setPackageId(pkg.getId());
                chunk.setVersionId(versionId);
                chunk.setChunkIndex(chunkIndex++);
                chunk.setHeading(part.heading());
                chunk.setContent(part.content());
                chunk.setTokenCount(Math.max(1, part.content().length() / 2));
                chunk.setAnchor(part.anchor());
                chunkRepository.save(chunk);
            }
        }

        boolean isCurrentVersion = versionId.equals(pkg.getCurrentVersionId());
        if (isCurrentVersion && result.qualityMeta() != null) {
            pkg.setQualityMeta(result.qualityMeta());
        }
        if (isCurrentVersion
                && (pkg.getTitle() == null || pkg.getTitle().equals("未命名知识包"))
                && result.detectedTitle() != null && !result.detectedTitle().isBlank()) {
            pkg.setTitle(result.detectedTitle().trim());
        }
        if (isCurrentVersion && pkg.getStatus() == KnowledgePackage.Status.draft) {
            pkg.setStatus(KnowledgePackage.Status.active);
        }
        packageRepository.save(pkg);
        version.setParseStatus(PackageVersion.ParseStatus.success);
        versionRepository.save(version);
        chunkRepository.flush();
        packageRepository.flush();
        versionRepository.flush();

        searchIndexService.indexVersion(pkg, version,
                documentRepository.findByVersionIdOrderByOrderNoAsc(versionId),
                chunkRepository.findByVersionId(versionId));
    }

    private boolean isParseCandidate(PackageAsset asset) {
        String path = asset.getPath().toLowerCase(java.util.Locale.ROOT);
        return asset.getRole() == PackageAsset.Role.entry
                || asset.getRole() == PackageAsset.Role.html
                || asset.getRole() == PackageAsset.Role.markdown
                || path.endsWith("content.js")
                || path.endsWith("_meta.json")
                || path.matches(".*\\.(txt|json|csv|log|xml|ya?ml)$");
    }
}

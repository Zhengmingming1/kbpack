package com.kbpack.pkg;

import com.kbpack.admin.OperationLogService;
import com.kbpack.common.error.ApiException;
import com.kbpack.common.error.ErrorCode;
import com.kbpack.search.SearchIndexUpdateCoordinator;
import com.kbpack.user.AppUser;
import org.springframework.data.domain.Sort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TagService {

    private final TagRepository tagRepository;
    private final PackageTagRepository packageTagRepository;
    private final PackageAccessService accessService;
    private final OperationLogService operationLogService;
    private final SearchIndexUpdateCoordinator searchIndexUpdates;

    public TagService(
            TagRepository tagRepository,
            PackageTagRepository packageTagRepository,
            PackageAccessService accessService,
            OperationLogService operationLogService,
            SearchIndexUpdateCoordinator searchIndexUpdates
    ) {
        this.tagRepository = tagRepository;
        this.packageTagRepository = packageTagRepository;
        this.accessService = accessService;
        this.operationLogService = operationLogService;
        this.searchIndexUpdates = searchIndexUpdates;
    }

    @Transactional(readOnly = true)
    public List<Tag> list() {
        return tagRepository.findAll(Sort.by(Sort.Direction.ASC, "name"));
    }

    @Transactional
    public Tag create(String rawName, AppUser actor, String ip) {
        accessService.requireContentWriter(actor);
        String name = normalizeName(rawName);
        if (tagRepository.existsByName(name)) {
            throw new ApiException(ErrorCode.TAG_NAME_DUPLICATE);
        }
        Tag tag = new Tag();
        tag.setName(name);
        try {
            tagRepository.saveAndFlush(tag);
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(ErrorCode.TAG_NAME_DUPLICATE);
        }
        operationLogService.record(
                actor.getId(), "tag.create", "tag", tag.getId(), Map.of("name", name), ip
        );
        return tag;
    }

    @Transactional
    public void delete(UUID tagId, AppUser actor, String ip) {
        accessService.requireContentWriter(actor);
        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "标签不存在"));
        List<UUID> affectedPackages = packageTagRepository.findAllByIdTagId(tagId).stream()
                .map(link -> link.getId().getPackageId()).toList();
        tagRepository.delete(tag);
        operationLogService.record(
                actor.getId(), "tag.delete", "tag", tagId, Map.of("name", tag.getName()), ip
        );
        searchIndexUpdates.refreshPackagesAfterCommit(affectedPackages);
    }

    @Transactional(readOnly = true)
    public long packageCount(UUID tagId) {
        return packageTagRepository.countActiveByTagId(tagId);
    }

    private String normalizeName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "标签名不能为空");
        }
        String name = rawName.trim();
        if (name.length() > 64) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "标签名不能超过 64 字符");
        }
        return name;
    }
}

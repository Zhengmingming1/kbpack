package com.kbpack.search;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class SearchIndexUpdateWorker {

    private final SearchIndexService searchIndexService;

    public SearchIndexUpdateWorker(SearchIndexService searchIndexService) {
        this.searchIndexService = searchIndexService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void refreshPackage(UUID packageId) {
        searchIndexService.reindexPackage(packageId);
    }

    public void deleteVersion(UUID versionId) {
        searchIndexService.deleteVersion(versionId);
    }
}

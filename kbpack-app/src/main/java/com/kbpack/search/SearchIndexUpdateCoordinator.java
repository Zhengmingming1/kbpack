package com.kbpack.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.UUID;

@Component
public class SearchIndexUpdateCoordinator {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexUpdateCoordinator.class);
    private final SearchIndexUpdateWorker worker;

    public SearchIndexUpdateCoordinator(SearchIndexUpdateWorker worker) {
        this.worker = worker;
    }

    public void refreshPackageAfterCommit(UUID packageId) {
        afterCommit(() -> safely("refresh package " + packageId, () -> worker.refreshPackage(packageId)));
    }

    public void refreshPackagesAfterCommit(Collection<UUID> packageIds) {
        LinkedHashSet<UUID> distinctIds = new LinkedHashSet<>(packageIds);
        afterCommit(() -> distinctIds.forEach(packageId ->
                safely("refresh package " + packageId, () -> worker.refreshPackage(packageId))));
    }

    public void deleteVersionAfterCommit(UUID versionId) {
        afterCommit(() -> safely("delete version " + versionId, () -> worker.deleteVersion(versionId)));
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private void safely(String operation, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException error) {
            // PostgreSQL remains authoritative; a later explicit rebuild can repair search.
            log.error("Failed to {} in Meilisearch", operation, error);
        }
    }
}

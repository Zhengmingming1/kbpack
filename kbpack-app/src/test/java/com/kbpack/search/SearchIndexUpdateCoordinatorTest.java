package com.kbpack.search;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class SearchIndexUpdateCoordinatorTest {

    @AfterEach
    void clearTransactionState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void defersRefreshUntilTransactionCommit() {
        SearchIndexUpdateWorker worker = mock(SearchIndexUpdateWorker.class);
        SearchIndexUpdateCoordinator coordinator = new SearchIndexUpdateCoordinator(worker);
        UUID packageId = UUID.randomUUID();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();

        coordinator.refreshPackageAfterCommit(packageId);

        verifyNoInteractions(worker);
        TransactionSynchronizationManager.getSynchronizations().forEach(synchronization -> synchronization.afterCommit());
        verify(worker).refreshPackage(packageId);
    }

    @Test
    void refreshesImmediatelyOutsideTransaction() {
        SearchIndexUpdateWorker worker = mock(SearchIndexUpdateWorker.class);
        SearchIndexUpdateCoordinator coordinator = new SearchIndexUpdateCoordinator(worker);
        UUID packageId = UUID.randomUUID();

        coordinator.refreshPackageAfterCommit(packageId);

        verify(worker).refreshPackage(packageId);
    }
}

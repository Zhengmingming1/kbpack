package com.kbpack.pkg;

import jakarta.persistence.Version;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OptimisticLockingContractTest {

    @Test
    void mutablePackageEntitiesUseOptimisticLocking() throws Exception {
        assertThat(KnowledgePackage.class.getDeclaredField("lockVersion").getAnnotation(Version.class))
                .isNotNull();
        assertThat(PackageVersion.class.getDeclaredField("lockVersion").getAnnotation(Version.class))
                .isNotNull();
    }
}

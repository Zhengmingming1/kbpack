package com.kbpack.pkg;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class PackageCollectionId implements Serializable {

    @Column(name = "package_id", nullable = false)
    private UUID packageId;

    @Column(name = "collection_id", nullable = false)
    private UUID collectionId;

    protected PackageCollectionId() {
    }

    public PackageCollectionId(UUID packageId, UUID collectionId) {
        this.packageId = packageId;
        this.collectionId = collectionId;
    }

    public UUID getPackageId() {
        return packageId;
    }

    public UUID getCollectionId() {
        return collectionId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PackageCollectionId that)) {
            return false;
        }
        return Objects.equals(packageId, that.packageId) && Objects.equals(collectionId, that.collectionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(packageId, collectionId);
    }
}

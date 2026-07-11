package com.kbpack.pkg;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "package_collection")
public class PackageCollection {

    @EmbeddedId
    private PackageCollectionId id;

    protected PackageCollection() {
    }

    public PackageCollection(PackageCollectionId id) {
        this.id = id;
    }

    public PackageCollectionId getId() {
        return id;
    }
}

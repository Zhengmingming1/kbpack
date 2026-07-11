package com.kbpack.pkg;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "package_tag")
public class PackageTag {

    @EmbeddedId
    private PackageTagId id;

    protected PackageTag() {
    }

    public PackageTag(PackageTagId id) {
        this.id = id;
    }

    public PackageTagId getId() {
        return id;
    }
}

package com.kbpack.pkg;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class PackageTagId implements Serializable {

    @Column(name = "package_id", nullable = false)
    private UUID packageId;

    @Column(name = "tag_id", nullable = false)
    private UUID tagId;

    protected PackageTagId() {
    }

    public PackageTagId(UUID packageId, UUID tagId) {
        this.packageId = packageId;
        this.tagId = tagId;
    }

    public UUID getPackageId() {
        return packageId;
    }

    public UUID getTagId() {
        return tagId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PackageTagId that)) {
            return false;
        }
        return Objects.equals(packageId, that.packageId) && Objects.equals(tagId, that.tagId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(packageId, tagId);
    }
}

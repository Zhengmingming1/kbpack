package com.kbpack.pkg;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class FavoriteId implements Serializable {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "package_id", nullable = false)
    private UUID packageId;

    protected FavoriteId() {
    }

    public FavoriteId(UUID userId, UUID packageId) {
        this.userId = userId;
        this.packageId = packageId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getPackageId() {
        return packageId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FavoriteId that)) {
            return false;
        }
        return Objects.equals(userId, that.userId) && Objects.equals(packageId, that.packageId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, packageId);
    }
}

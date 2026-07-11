package com.kbpack.common.id;

import com.github.f4b6a3.uuid.UuidCreator;

import java.util.UUID;

/**
 * UUID v7 generator (time-ordered). Used by JPA IdentifierGenerator in P1+.
 */
public final class UuidV7Generator {

    private UuidV7Generator() {
    }

    public static UUID generate() {
        return UuidCreator.getTimeOrderedEpoch();
    }
}

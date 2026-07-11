package com.kbpack.common.id;

import java.util.Locale;
import java.util.UUID;

/**
 * API-layer ID prefix codec (docs/00-canonical-spec.md §3.4).
 * DB stores raw UUID; API exposes {prefix}_{uuid}.
 */
public enum IdPrefix {
    USER("usr"),
    PACKAGE("pkg"),
    VERSION("ver"),
    ASSET("ast"),
    DOCUMENT("doc"),
    CHUNK("chk"),
    TAG("tag"),
    COLLECTION("col"),
    TASK("tsk"),
    LOG("log");

    private final String value;

    IdPrefix(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public String format(UUID id) {
        return value + "_" + id.toString();
    }

    public UUID parse(String externalId) {
        if (externalId == null || externalId.isBlank()) {
            throw new IllegalArgumentException("id is blank");
        }
        String expected = value + "_";
        if (!externalId.regionMatches(true, 0, expected, 0, expected.length())) {
            throw new IllegalArgumentException("id must start with " + expected);
        }
        return UUID.fromString(externalId.substring(expected.length()));
    }

    public static IdPrefix fromExternal(String externalId) {
        if (externalId == null || !externalId.contains("_")) {
            throw new IllegalArgumentException("invalid external id: " + externalId);
        }
        String prefix = externalId.substring(0, externalId.indexOf('_')).toLowerCase(Locale.ROOT);
        for (IdPrefix p : values()) {
            if (p.value.equals(prefix)) {
                return p;
            }
        }
        throw new IllegalArgumentException("unknown id prefix: " + prefix);
    }
}

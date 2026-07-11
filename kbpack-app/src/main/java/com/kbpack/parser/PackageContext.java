package com.kbpack.parser;

import com.kbpack.pkg.KnowledgePackage;
import com.kbpack.pkg.PackageVersion;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public record PackageContext(
        KnowledgePackage knowledgePackage,
        PackageVersion version,
        Map<String, byte[]> files
) {
    public PackageContext {
        files = new LinkedHashMap<>(files);
    }

    public boolean has(String path) {
        return files.keySet().stream().anyMatch(key -> key.equalsIgnoreCase(path));
    }

    public String text(String path) {
        return files.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(path))
                .findFirst()
                .map(entry -> new String(entry.getValue(), StandardCharsets.UTF_8))
                .orElse(null);
    }
}

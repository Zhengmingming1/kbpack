package com.kbpack.common.archive;

public final class ArchivePaths {
    private ArchivePaths() {}

    public static boolean isPlatformMetadata(String path) {
        if (path == null || path.isBlank()) return false;
        String normalized = path.replace('\\', '/');
        for (String segment : normalized.split("/")) {
            if (segment.equalsIgnoreCase("__MACOSX")) return true;
        }
        String name = normalized.substring(normalized.lastIndexOf('/') + 1);
        return name.equalsIgnoreCase(".DS_Store") || name.startsWith("._");
    }
}

package com.kbpack.pkg;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class PackageCoverSelector {

    private PackageCoverSelector() {
    }

    public static String select(List<PackageAsset> assets) {
        if (assets == null) return null;
        return assets.stream()
                .filter(asset -> asset.getRole() == PackageAsset.Role.image)
                .sorted(Comparator.comparingInt(PackageCoverSelector::priority)
                        .thenComparing(PackageAsset::getPath, String.CASE_INSENSITIVE_ORDER))
                .map(PackageAsset::getPath)
                .findFirst()
                .orElse(null);
    }

    private static int priority(PackageAsset asset) {
        String path = asset.getPath().toLowerCase(Locale.ROOT);
        String filename = path.substring(path.lastIndexOf('/') + 1);
        if (filename.contains("cover")) return 0;
        if (filename.contains("thumbnail") || filename.contains("thumb")
                || filename.contains("poster") || filename.contains("preview")
                || filename.contains("hero")) return 1;
        if (filename.contains("logo") || filename.contains("icon") || filename.contains("favicon")) return 3;
        return 2;
    }
}

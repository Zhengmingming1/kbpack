package com.kbpack.pkg;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PackageCoverSelectorTest {

    @Test
    void prefersSemanticCoverOverLogosAndOtherImages() {
        assertThat(PackageCoverSelector.select(List.of(
                image("assets/logo.png"),
                image("assets/photo.jpg"),
                image("assets/guide-cover.webp")
        ))).isEqualTo("assets/guide-cover.webp");
    }

    @Test
    void returnsNullWhenVersionHasNoImages() {
        PackageAsset markdown = new PackageAsset();
        markdown.setPath("index.md");
        markdown.setRole(PackageAsset.Role.markdown);

        assertThat(PackageCoverSelector.select(List.of(markdown))).isNull();
    }

    private static PackageAsset image(String path) {
        PackageAsset asset = new PackageAsset();
        asset.setPath(path);
        asset.setRole(PackageAsset.Role.image);
        return asset;
    }
}

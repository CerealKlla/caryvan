package com.github.cerealklla.caryvan.wainwright.preview;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import net.neoforged.fml.loading.FMLPaths;

/**
 * Shared, file-based hand-off to the standalone CaryvanImager process (own Gradle module, see
 * {@code CaryvanImager/CLAUDE.md} -- a direct parallel of Blueprynts' own BluepryntImager/
 * BlueprintStorage relationship). Unlike a Blueprint, a (type, bodySpecies, wheelSpecies) combo is
 * immutable once rendered -- there's no re-save/edit concept -- so there's no mtime/freshness
 * tracking here at all, just "does the output exist yet."
 *
 * <p>Directory layout: {@code <gameDir>/caryvanimager/requests/<key>.request.json} (written by
 * {@link #ensureRequested}) and {@code <key>.png}/{@code <key>.small.png} (written by the imager
 * once it picks the request up).
 */
public final class WainwrightPreviewStorage {

    private static final WainwrightPreviewStorage INSTANCE = new WainwrightPreviewStorage();

    private final Path requestsDir;

    private WainwrightPreviewStorage() {
        this.requestsDir = FMLPaths.GAMEDIR.get().resolve("caryvanimager").resolve("requests");
    }

    public static WainwrightPreviewStorage get() {
        return INSTANCE;
    }

    public static String keyFor(final String type, final String bodySpecies, final String wheelSpecies) {
        return type + "_" + bodySpecies + "_" + wheelSpecies;
    }

    /** Writes the request file if it doesn't already exist -- idempotent, safe to call on every request from a client, not just the first. */
    public void ensureRequested(final String type, final String bodySpecies, final String wheelSpecies) {
        final String key = keyFor(type, bodySpecies, wheelSpecies);
        final Path requestFile = requestsDir.resolve(key + ".request.json");
        if (Files.exists(requestFile)) {
            return;
        }
        try {
            Files.createDirectories(requestsDir);
            final String json = "{\"type\": \"" + type + "\", \"bodySpecies\": \"" + bodySpecies + "\", \"wheelSpecies\": \"" + wheelSpecies + "\"}";
            Files.writeString(requestFile, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            // Worst case the client just never gets a preview for this combo -- not fatal.
        }
    }

    public Optional<byte[]> readPngBytes(final String key) {
        final Path pngFile = requestsDir.resolve(key + ".png");
        if (!Files.exists(pngFile)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(pngFile));
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}

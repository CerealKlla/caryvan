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

    // Bump this whenever CaryvanImager's renderer output for an existing combo would change
    // meaningfully (e.g. a geometry port) -- real live-test bug, 2026-10-28: a client that had
    // already fetched+cached a combo's image (ClientWainwrightPreviewCache persists to disk,
    // GAMEDIR/caryvan/preview_cache/, forever -- by design, since a combo was assumed immutable)
    // kept showing the OLD placeholder-box render even after the server-side renderer was replaced
    // with a real geometry port, since nothing about the KEY changed to signal "this is different
    // now." Folding a version into the key makes a renderer change naturally orphan every old
    // server-side file AND force every client to re-request+re-cache under a new key, with no
    // server or client cache to manually clear.
    // v3, 2026-10-28: the v2 geometry port itself rendered upside down (missing the standard
    // vanilla entity-model orientation correction, see CaryvanImager's own decisions.md) -- bumped
    // again so clients don't keep showing the now-fixed-server-side-but-still-cached-wrong v2 images.
    private static final String GEOMETRY_VERSION = "v3";

    public static String keyFor(final String type, final String bodySpecies, final String wheelSpecies) {
        return GEOMETRY_VERSION + "_" + type + "_" + bodySpecies + "_" + wheelSpecies;
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

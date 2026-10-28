package com.github.cerealklla.caryvan.wainwright.preview.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.github.cerealklla.caryvan.CaryvanMod;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Client-only (touches {@code Minecraft}/texture registration directly -- never referenced from
 * common code, same isolation {@code WainwrightPreviewImageReceivedEvent} exists to enforce).
 * Mirrors Blueprynts' own {@code ClientBlueprintPreviewCache} shape, simplified: a (type,
 * bodySpecies, wheelSpecies) combo's image never changes once rendered, so there's no mtime
 * comparison at all -- once a key is cached (memory or the on-disk {@code
 * FMLPaths.GAMEDIR/caryvan/preview_cache/} store, which survives between client sessions), it's
 * cached forever.
 */
public final class ClientWainwrightPreviewCache {

    private static final Path CACHE_ROOT = FMLPaths.GAMEDIR.get().resolve("caryvan").resolve("preview_cache");
    private static final Map<String, Identifier> MEMORY = new ConcurrentHashMap<>();

    private ClientWainwrightPreviewCache() {
    }

    /** @return a registered texture {@link Identifier} if already cached (memory or disk) -- ready to {@code blit} immediately. Empty means the caller should send a {@code RequestWainwrightPreviewPayload} and wait for {@link #store}. */
    public static Optional<Identifier> get(final String key) {
        final Identifier cached = MEMORY.get(key);
        if (cached != null) {
            return Optional.of(cached);
        }
        final Path imageFile = imageFileFor(key);
        if (!Files.exists(imageFile)) {
            return Optional.empty();
        }
        try {
            final byte[] bytes = Files.readAllBytes(imageFile);
            final Identifier textureId = registerTexture(key, bytes);
            MEMORY.put(key, textureId);
            return Optional.of(textureId);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /** Called once a {@code WainwrightPreviewImagePayload} arrives -- persists to disk and registers the texture. */
    public static void store(final String key, final byte[] pngBytes) {
        try {
            Files.createDirectories(CACHE_ROOT);
            Files.write(imageFileFor(key), pngBytes);
        } catch (IOException e) {
            // Worst case this combo gets re-fetched next time -- not fatal, just less caching.
        }
        MEMORY.put(key, registerTexture(key, pngBytes));
    }

    private static Identifier registerTexture(final String key, final byte[] pngBytes) {
        final Identifier textureId = Identifier.fromNamespaceAndPath(CaryvanMod.MODID, "wainwright_preview/" + sanitizeForIdentifier(key));
        try {
            final NativeImage image = NativeImage.read(pngBytes);
            final DynamicTexture texture = new DynamicTexture(() -> "caryvan wainwright preview: " + key, image);
            Minecraft.getInstance().getTextureManager().register(textureId, texture);
        } catch (IOException e) {
            throw new RuntimeException("Failed to decode Wainwright Station preview image for '" + key + "'", e);
        }
        return textureId;
    }

    private static Path imageFileFor(final String key) {
        return CACHE_ROOT.resolve(sanitize(key) + ".png");
    }

    private static String sanitize(final String value) {
        return value.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    /** Same {@code Identifier}-strictness reasoning as Blueprynts' own cache -- lossy on purpose, see that class's own doc. */
    private static String sanitizeForIdentifier(final String value) {
        return value.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
    }
}

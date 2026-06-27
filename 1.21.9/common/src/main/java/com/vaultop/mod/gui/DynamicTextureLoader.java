package com.vaultop.mod.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

/**
 * Downloads tournament thumbnail images, saves them to a local cache directory,
 * and creates Minecraft textures from the cached files.
 */
public class DynamicTextureLoader {
    private static final ConcurrentHashMap<String, Identifier> LOADED_TEXTURES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> LOADING_TEXTURES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> FAILED_TEXTURES = new ConcurrentHashMap<>();
    // Store actual image dimensions so drawTexture can use correct UV mapping
    private static final ConcurrentHashMap<String, int[]> TEXTURE_DIMENSIONS = new ConcurrentHashMap<>();

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static Path getCacheDir() {
        Path cacheDir = MinecraftClient.getInstance().runDirectory.toPath().resolve("vaultop_cache").resolve("thumbnails");
        try {
            Files.createDirectories(cacheDir);
        } catch (Exception e) {
            System.err.println("[VaultOP] Failed to create cache dir: " + e.getMessage());
        }
        return cacheDir;
    }

    /**
     * Get the actual dimensions of a loaded texture.
     * Returns [width, height] or null if not loaded yet.
     */
    public static int[] getTextureDimensions(String id) {
        String cleanId = id.toLowerCase().replaceAll("[^a-z0-9_]", "_");
        return TEXTURE_DIMENSIONS.get(cleanId);
    }

    /**
     * Returns an Identifier for the texture if loaded, or null if still loading/failed.
     */
    public static Identifier getOrLoad(String url, String id) {
        if (url == null || url.isEmpty()) return null;

        String cleanId = id.toLowerCase().replaceAll("[^a-z0-9_]", "_");

        // Already loaded?
        if (LOADED_TEXTURES.containsKey(cleanId)) {
            return LOADED_TEXTURES.get(cleanId);
        }

        // Already failed? Don't retry.
        if (FAILED_TEXTURES.containsKey(cleanId)) {
            return null;
        }

        // Already loading?
        if (LOADING_TEXTURES.containsKey(cleanId)) {
            return null;
        }

        LOADING_TEXTURES.put(cleanId, Boolean.TRUE);

        Path cacheDir = getCacheDir();
        Path cachedFile = cacheDir.resolve(cleanId + ".png");

        // Check if we have a cached file on disk
        if (Files.exists(cachedFile)) {
            System.out.println("[VaultOP] Loading cached thumbnail for: " + id);
            loadTextureFromFile(cachedFile, cleanId);
            return null;
        }

        // Need to download/decode
        if (url.startsWith("data:image")) {
            System.out.println("[VaultOP] Decoding base64 thumbnail for: " + id + " (length: " + url.length() + ")");
            CompletableFuture.runAsync(() -> {
                try {
                    int commaIdx = url.indexOf(",");
                    if (commaIdx == -1) {
                        markFailed(cleanId, "No comma in base64 data URI");
                        return;
                    }
                    String base64Str = url.substring(commaIdx + 1).replaceAll("\\s+", "");
                    byte[] bytes = java.util.Base64.getMimeDecoder().decode(base64Str);
                    System.out.println("[VaultOP] Decoded " + bytes.length + " bytes for " + id);

                    // Save to cache file
                    Files.write(cachedFile, bytes);
                    System.out.println("[VaultOP] Saved to cache: " + cachedFile);

                    // Now load from the cache file
                    loadTextureFromFile(cachedFile, cleanId);
                } catch (Exception e) {
                    markFailed(cleanId, "Base64 decode error: " + e.getMessage());
                }
            });
            return null;
        }

        // HTTP URL
        String fullUrl = url;
        if (url.startsWith("/")) {
            String backendUrl = com.vaultop.mod.VaultOPMod.getInstance().getConfigManager().getBackendUrl();
            if (backendUrl.endsWith("/")) {
                backendUrl = backendUrl.substring(0, backendUrl.length() - 1);
            }
            fullUrl = backendUrl + url;
        }

        System.out.println("[VaultOP] Downloading thumbnail for: " + id + " from " + fullUrl);
        final String finalUrl = fullUrl;
        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(finalUrl))
                        .GET()
                        .build();

                HttpResponse<byte[]> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() == 200 && response.body().length > 0) {
                    // Save to cache
                    Files.write(cachedFile, response.body());
                    System.out.println("[VaultOP] Downloaded and cached: " + cachedFile + " (" + response.body().length + " bytes)");

                    loadTextureFromFile(cachedFile, cleanId);
                } else {
                    markFailed(cleanId, "HTTP " + response.statusCode() + " from " + finalUrl);
                }
            } catch (Exception e) {
                markFailed(cleanId, "Download error: " + e.getMessage());
            }
        });

        return null;
    }

    /**
     * Load a PNG file from disk into a Minecraft texture on the render thread.
     */
    private static void loadTextureFromFile(Path file, String cleanId) {
        CompletableFuture.runAsync(() -> {
            try (FileInputStream fis = new FileInputStream(file.toFile())) {
                NativeImage nativeImage = NativeImage.read(fis);
                int imgW = nativeImage.getWidth();
                int imgH = nativeImage.getHeight();
                System.out.println("[VaultOP] Image loaded: " + imgW + "x" + imgH + " for " + cleanId);

                Identifier identifier = Identifier.of("vaultop", "tournament_thumb_" + cleanId);

                MinecraftClient.getInstance().execute(() -> {
                    try {
                        NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "vaultop_thumb_" + cleanId, nativeImage);
                        MinecraftClient.getInstance().getTextureManager().registerTexture(identifier, texture);
                        texture.upload();

                        TEXTURE_DIMENSIONS.put(cleanId, new int[]{imgW, imgH});
                        LOADED_TEXTURES.put(cleanId, identifier);
                        LOADING_TEXTURES.remove(cleanId);
                        System.out.println("[VaultOP] Texture ready: " + cleanId + " (" + imgW + "x" + imgH + ")");
                    } catch (Exception e) {
                        markFailed(cleanId, "Texture register error: " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                markFailed(cleanId, "File read error: " + e.getMessage());
                // Delete corrupt cache file
                try { Files.deleteIfExists(file); } catch (Exception ignored) {}
            }
        });
    }

    private static void markFailed(String cleanId, String reason) {
        System.err.println("[VaultOP] Thumbnail FAILED for " + cleanId + ": " + reason);
        FAILED_TEXTURES.put(cleanId, Boolean.TRUE);
        LOADING_TEXTURES.remove(cleanId);
    }
}

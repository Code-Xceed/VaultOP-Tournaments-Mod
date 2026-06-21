package com.vaultop.mod.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class DynamicTextureLoader {
    private static final Map<String, Identifier> LOADED_TEXTURES = new HashMap<>();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public static Identifier getOrLoad(String url, String id) {
        if (url == null || url.trim().isEmpty()) {
            return null;
        }
        
        // Check if relative URL, if so prepend backend URL
        String fullUrl = url;
        if (url.startsWith("/")) {
            String backendUrl = com.vaultop.mod.VaultOPMod.getInstance().getConfigManager().getBackendUrl();
            if (backendUrl.endsWith("/")) {
                backendUrl = backendUrl.substring(0, backendUrl.length() - 1);
            }
            fullUrl = backendUrl + url;
        }

        if (LOADED_TEXTURES.containsKey(fullUrl)) {
            return LOADED_TEXTURES.get(fullUrl);
        }

        // Register texture identifier mapping
        String cleanId = id.toLowerCase().replaceAll("[^a-z0-9_]", "_");
        Identifier identifier = Identifier.of("vaultop", "tournament_thumb_" + cleanId);
        LOADED_TEXTURES.put(fullUrl, identifier); // Put it first to prevent duplicate async requests

        final String finalUrl = fullUrl;
        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(finalUrl))
                        .GET()
                        .build();

                HttpResponse<InputStream> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() == 200) {
                    try (InputStream is = response.body()) {
                        NativeImage nativeImage = NativeImage.read(is);
                        MinecraftClient.getInstance().execute(() -> {
                            NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "vaultop_dynamic_" + cleanId, nativeImage);
                            MinecraftClient.getInstance().getTextureManager().registerTexture(identifier, texture);
                        });
                    }
                }
            } catch (Exception e) {
                System.err.println("Failed to load dynamic image from: " + finalUrl + " - " + e.getMessage());
            }
        });

        return identifier;
    }
}

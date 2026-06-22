package com.vaultop.mod.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vaultop.mod.VaultOPMod;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class RESTClient {
    private final HttpClient httpClient;

    public RESTClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    private String getBaseUrl() {
        return VaultOPMod.getInstance().getConfigManager().getBackendUrl();
    }

    public CompletableFuture<JsonObject> fetchProfile(String token) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/auth/me"))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        return JsonParser.parseString(response.body()).getAsJsonObject();
                    }
                    throw new RuntimeException("HTTP Status " + response.statusCode());
                });
    }

    public CompletableFuture<JsonArray> fetchTournaments() {
        String token = VaultOPMod.getInstance().getSessionManager().getSessionToken();
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/tournaments"))
                .GET();
        if (token != null && !token.trim().isEmpty()) {
            builder.header("Authorization", "Bearer " + token);
        }

        return httpClient.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        return JsonParser.parseString(response.body()).getAsJsonArray();
                    }
                    throw new RuntimeException("HTTP Status " + response.statusCode());
                });
    }

    private String encodePathSegment(String segment) {
        return java.net.URLEncoder.encode(segment, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    public CompletableFuture<JsonObject> fetchRegistrationStatus(String tournamentId, String token) {
        String encodedId = encodePathSegment(tournamentId);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/tournaments/" + encodedId + "/registration-status"))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        return JsonParser.parseString(response.body()).getAsJsonObject();
                    }
                    throw new RuntimeException("HTTP Status " + response.statusCode());
                });
    }

    public CompletableFuture<JsonObject> fetchProtectedConfig(String tournamentId) {
        String encodedId = encodePathSegment(tournamentId);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/protected-mode/" + encodedId + "/config"))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        return JsonParser.parseString(response.body()).getAsJsonObject();
                    }
                    throw new RuntimeException("HTTP Status " + response.statusCode());
                });
    }

    public CompletableFuture<Boolean> submitVerificationLog(String tournamentId, String token, JsonObject verificationData) {
        String encodedId = encodePathSegment(tournamentId);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/protected-mode/" + encodedId + "/verify"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(verificationData.toString()))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> response.statusCode() == 200 || response.statusCode() == 201);
    }

    public CompletableFuture<JsonObject> fetchBrackets(String tournamentId) {
        String encodedId = encodePathSegment(tournamentId);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/brackets/" + encodedId))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        return JsonParser.parseString(response.body()).getAsJsonObject();
                    }
                    throw new RuntimeException("HTTP Status " + response.statusCode());
                });
    }

    public CompletableFuture<com.google.gson.JsonArray> fetchAnnouncements() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/announcements"))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        return JsonParser.parseString(response.body()).getAsJsonArray();
                    }
                    throw new RuntimeException("HTTP Status " + response.statusCode());
                });
    }
}

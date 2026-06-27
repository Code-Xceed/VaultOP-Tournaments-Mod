package com.vaultop.mod.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vaultop.mod.VaultOPMod;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

public class WebSocketManager {
    private final HttpClient httpClient;
    private WebSocket webSocket;
    private final String token;
    private Consumer<JsonObject> messageListener;
    private boolean isClosing = false;

    public WebSocketManager(String token) {
        this.token = token;
        this.httpClient = HttpClient.newHttpClient();
    }

    private String getWsUrl() {
        return VaultOPMod.getInstance().getConfigManager().getWebSocketUrl();
    }

    public void setMessageListener(Consumer<JsonObject> listener) {
        this.messageListener = listener;
    }

    public void connect() {
        if (token == null || token.isEmpty()) return;
        
        isClosing = false;
        String uriStr = getWsUrl() + "?token=" + token;
        
        httpClient.newWebSocketBuilder()
                .buildAsync(URI.create(uriStr), new WebSocketListener())
                .thenAccept(ws -> {
                    this.webSocket = ws;
                    VaultOPMod.LOGGER.info("[VaultOP] WebSocket connected.");
                })
                .exceptionally(ex -> {
                    VaultOPMod.LOGGER.error("[VaultOP] WebSocket connection failed, retrying in 5s...", ex);
                    scheduleReconnect();
                    return null;
                });
    }

    private void scheduleReconnect() {
        if (isClosing) return;
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                if (!isClosing) {
                    VaultOPMod.LOGGER.info("[VaultOP] WebSocket reconnecting...");
                    connect();
                }
            } catch (InterruptedException ignored) {}
        }).start();
    }

    public void disconnect() {
        isClosing = true;
        if (webSocket != null) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Disconnecting");
        }
    }

    private class WebSocketListener implements WebSocket.Listener {
        private final StringBuilder textBuffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            com.google.gson.JsonObject identify = new com.google.gson.JsonObject();
            identify.addProperty("type", "IDENTIFY");
            identify.addProperty("token", token);
            webSocket.sendText(identify.toString(), true);
            
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            textBuffer.append(data);
            if (last) {
                String fullMessage = textBuffer.toString();
                textBuffer.setLength(0);
                
                try {
                    JsonObject json = JsonParser.parseString(fullMessage).getAsJsonObject();
                    if (messageListener != null) {
                        messageListener.accept(json);
                    }
                } catch (Exception e) {
                    VaultOPMod.LOGGER.error("[VaultOP] Error parsing WebSocket message: " + fullMessage, e);
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            VaultOPMod.LOGGER.info("[VaultOP] WebSocket closed. Code: " + statusCode + ", Reason: " + reason);
            if (!isClosing) {
                scheduleReconnect();
            }
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            VaultOPMod.LOGGER.error("[VaultOP] WebSocket error", error);
            if (!isClosing) {
                scheduleReconnect();
            }
        }
    }
}

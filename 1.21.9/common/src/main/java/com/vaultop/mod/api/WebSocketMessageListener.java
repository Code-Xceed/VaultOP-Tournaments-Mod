package com.vaultop.mod.api;

import com.google.gson.JsonObject;

public interface WebSocketMessageListener {
    void onWebSocketMessage(JsonObject json);
}

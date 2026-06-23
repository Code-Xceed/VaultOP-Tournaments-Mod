package com.vaultop.mod;

import com.google.gson.JsonObject;
import com.vaultop.mod.auth.SessionManager;
import com.vaultop.mod.api.WebSocketMessageListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VaultOPMod {
    public static final String MOD_ID = "vaultop";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    
    private static VaultOPMod instance;
    private SessionManager sessionManager;
    private com.vaultop.mod.auth.ConfigManager configManager;
    private com.vaultop.mod.api.RESTClient restClient;
    private com.vaultop.mod.api.WebSocketManager webSocketManager;

    public static void init() {
        instance = new VaultOPMod();
        instance.onInitialize();
    }

    public void onInitialize() {
        LOGGER.info("[VaultOP] Initializing VaultOP Mod client components...");
        
        // Initialize Discord Rich Presence
        com.vaultop.mod.discord.DiscordPresenceManager.init();

        // Initialize config manager first
        this.configManager = new com.vaultop.mod.auth.ConfigManager();
        this.configManager.loadConfig();

        // Initialize REST client
        this.restClient = new com.vaultop.mod.api.RESTClient();
        
        // Initialize the Session Manager
        this.sessionManager = new SessionManager();
        this.sessionManager.loadSession();

        if (this.sessionManager.isAuthenticated()) {
            LOGGER.info("[VaultOP] Session active. Starting WebSocket client...");
            startWebSocket(this.sessionManager.getSessionToken());
        } else {
            LOGGER.warn("[VaultOP] No active session found. Authentication required.");
        }
    }

    public void startWebSocket(String token) {
        if (this.webSocketManager != null) {
            this.webSocketManager.disconnect();
        }
        this.webSocketManager = new com.vaultop.mod.api.WebSocketManager(token);
        this.webSocketManager.setMessageListener(this::handleWebSocketMessage);
        this.webSocketManager.connect();
    }

    private void handleWebSocketMessage(JsonObject json) {
        if (json.has("type") && !json.get("type").isJsonNull()) {
            String type = json.get("type").getAsString();
            
            if ("ANNOUNCEMENT".equals(type)) {
                MinecraftClient.getInstance().execute(() -> {
                    try {
                        MinecraftClient.getInstance().getSoundManager().play(
                            PositionedSoundInstance.master(SoundEvents.BLOCK_BELL_USE, 1.0f)
                        );
                        
                        if (MinecraftClient.getInstance().player != null) {
                            String title = json.has("title") ? json.get("title").getAsString() : "New Announcement";
                            String desc = json.has("desc") ? json.get("desc").getAsString() : "";
                            MinecraftClient.getInstance().player.sendMessage(
                                Text.literal("§6[VaultOP Announcement] §e" + title + ": §f" + desc),
                                false
                            );
                        }
                    } catch (Exception e) {
                        LOGGER.error("Failed to process announcement sound/message", e);
                    }
                });
            }
        }
        
        MinecraftClient.getInstance().execute(() -> {
            net.minecraft.client.gui.screen.Screen currentScreen = MinecraftClient.getInstance().currentScreen;
            if (currentScreen instanceof WebSocketMessageListener) {
                ((WebSocketMessageListener) currentScreen).onWebSocketMessage(json);
            }
        });
    }

    public void stopWebSocket() {
        if (this.webSocketManager != null) {
            this.webSocketManager.disconnect();
            this.webSocketManager = null;
        }
    }

    public static VaultOPMod getInstance() {
        return instance;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    public com.vaultop.mod.api.RESTClient getRestClient() {
        return restClient;
    }

    public com.vaultop.mod.api.WebSocketManager getWebSocketManager() {
        return webSocketManager;
    }

    public com.vaultop.mod.auth.ConfigManager getConfigManager() {
        return configManager;
    }
}

package com.vaultop.mod;

import com.vaultop.mod.auth.SessionManager;
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
        this.webSocketManager.connect();
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

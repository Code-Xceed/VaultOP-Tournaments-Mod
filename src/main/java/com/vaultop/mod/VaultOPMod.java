package com.vaultop.mod;

import com.vaultop.mod.auth.SessionManager;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VaultOPMod implements ModInitializer {
    public static final String MOD_ID = "vaultop";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    
    private static VaultOPMod instance;
    private SessionManager sessionManager;

    @Override
    public void onInitialize() {
        instance = this;
        LOGGER.info("[VaultOP] Initializing VaultOP Mod client components...");
        
        // Initialize the Session Manager
        this.sessionManager = new SessionManager();
        this.sessionManager.loadSession();

        if (this.sessionManager.isAuthenticated()) {
            LOGGER.info("[VaultOP] Session active. User: " + this.sessionManager.getSessionToken());
        } else {
            LOGGER.warn("[VaultOP] No active session found. Authentication required.");
        }
    }

    public static VaultOPMod getInstance() {
        return instance;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }
}

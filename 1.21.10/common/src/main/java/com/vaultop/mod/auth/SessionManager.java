package com.vaultop.mod.auth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.vaultop.mod.VaultOPMod;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class SessionManager {
    private static final String CONFIG_DIR = "vaultop";
    private static final String SESSION_FILE = CONFIG_DIR + "/session.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private SessionData sessionData;

    public void loadSession() {
        File file = new File(SESSION_FILE);
        if (!file.exists()) {
            this.sessionData = new SessionData(null);
            return;
        }

        try (FileReader reader = new FileReader(file)) {
            this.sessionData = GSON.fromJson(reader, SessionData.class);
            if (this.sessionData == null) {
                this.sessionData = new SessionData(null);
            }
        } catch (IOException e) {
            VaultOPMod.LOGGER.error("[VaultOP] Failed to load session", e);
            this.sessionData = new SessionData(null);
        }
    }

    public void saveSession(String token) {
        File dir = new File(CONFIG_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        this.sessionData = new SessionData(token);
        try (FileWriter writer = new FileWriter(SESSION_FILE)) {
            GSON.toJson(this.sessionData, writer);
            VaultOPMod.LOGGER.info("[VaultOP] Session saved successfully.");
        } catch (IOException e) {
            VaultOPMod.LOGGER.error("[VaultOP] Failed to save session", e);
        }
    }

    public void clearSession() {
        this.sessionData = new SessionData(null);
        File file = new File(SESSION_FILE);
        if (file.exists()) {
            file.delete();
            VaultOPMod.LOGGER.info("[VaultOP] Local session cleared.");
        }
    }

    public boolean isAuthenticated() {
        return sessionData != null && sessionData.token != null && !sessionData.token.trim().isEmpty();
    }

    public String getSessionToken() {
        return sessionData != null ? sessionData.token : null;
    }

    private static class SessionData {
        private final String token;

        public SessionData(String token) {
            this.token = token;
        }
    }
}

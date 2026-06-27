package com.vaultop.mod.auth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.vaultop.mod.VaultOPMod;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class ConfigManager {
    private static final String CONFIG_DIR = "vaultop";
    private static final String CONFIG_FILE = CONFIG_DIR + "/config.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ConfigData configData;

    public void loadConfig() {
        File file = new File(CONFIG_FILE);
        if (!file.exists()) {
            // Generate default config with production URLs
            this.configData = new ConfigData("https://vaultop-tournament-org.onrender.com", "https://vault-op-tournaments.vercel.app");
            saveConfig();
            return;
        }

        try (FileReader reader = new FileReader(file)) {
            this.configData = GSON.fromJson(reader, ConfigData.class);
            if (this.configData == null) {
                this.configData = new ConfigData("https://vaultop-tournament-org.onrender.com", "https://vault-op-tournaments.vercel.app");
            }
        } catch (IOException e) {
            VaultOPMod.LOGGER.error("[VaultOP] Failed to load config", e);
            this.configData = new ConfigData("https://vaultop-tournament-org.onrender.com", "https://vault-op-tournaments.vercel.app");
        }
    }

    public void saveConfig() {
        File dir = new File(CONFIG_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(this.configData, writer);
            VaultOPMod.LOGGER.info("[VaultOP] Config saved successfully.");
        } catch (IOException e) {
            VaultOPMod.LOGGER.error("[VaultOP] Failed to save config", e);
        }
    }

    public String getBackendUrl() {
        return configData != null ? configData.backendUrl : "https://vaultop-tournament-org.onrender.com";
    }

    public String getWebsiteUrl() {
        return configData != null ? configData.websiteUrl : "https://vault-op-tournaments.vercel.app";
    }

    public String getWebSocketUrl() {
        String base = getBackendUrl();
        if (base.startsWith("https://")) {
            return base.replace("https://", "wss://") + "/api/ws";
        } else {
            return base.replace("http://", "ws://") + "/api/ws";
        }
    }

    private static class ConfigData {
        private final String backendUrl;
        private final String websiteUrl;

        public ConfigData(String backendUrl, String websiteUrl) {
            this.backendUrl = backendUrl;
            this.websiteUrl = websiteUrl;
        }
    }
}

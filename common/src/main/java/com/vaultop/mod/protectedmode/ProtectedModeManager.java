package com.vaultop.mod.protectedmode;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.vaultop.mod.VaultOPMod;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class ProtectedModeManager {
    public static class ModInfo {
        public final String id;
        public final String version;
        public final String filename;
        public final String hash;

        public ModInfo(String id, String version, String filename, String hash) {
            this.id = id;
            this.version = version;
            this.filename = filename;
            this.hash = hash;
        }
    }

    public static class VerificationResult {
        public final boolean compliant;
        public final List<ModInfo> violatingMods;

        public VerificationResult(boolean compliant, List<ModInfo> violatingMods) {
            this.compliant = compliant;
            this.violatingMods = violatingMods;
        }
    }

    public static List<ModInfo> scanEnvironment() {
        List<ModInfo> mods = new ArrayList<>();
        for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
            ModMetadata meta = container.getMetadata();
            String id = meta.getId();
            String version = meta.getVersion().getFriendlyString();
            
            // Skip core Minecraft, Java, or Fabric Loader themselves to avoid noise
            if (id.equals("minecraft") || id.equals("java") || id.equals("fabricloader")) {
                continue;
            }

            String filename = "unknown";
            String hash = "";
            try {
                List<Path> paths = container.getOrigin().getPaths();
                if (!paths.isEmpty()) {
                    Path firstPath = paths.get(0);
                    filename = firstPath.getFileName().toString();
                    if (Files.isRegularFile(firstPath)) {
                        hash = computeSHA256(firstPath);
                    } else {
                        hash = "directory-dev";
                    }
                }
            } catch (Throwable ignored) {}

            mods.add(new ModInfo(id, version, filename, hash));
        }
        return mods;
    }

    private static String computeSHA256(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream is = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            byte[] hash = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public static CompletableFuture<VerificationResult> verify(String tournamentId) {
        return VaultOPMod.getInstance().getRestClient().fetchProtectedConfig(tournamentId)
                .thenApply(config -> {
                    // Config format: { "allowedMods": [{"id": "sodium", "hash": "optional_hash"}] }
                    JsonArray allowedArray = config.getAsJsonArray("allowedMods");
                    Map<String, JsonObject> allowedMap = new HashMap<>();
                    if (allowedArray != null) {
                        for (int i = 0; i < allowedArray.size(); i++) {
                            JsonObject obj = allowedArray.get(i).getAsJsonObject();
                            allowedMap.put(obj.get("id").getAsString(), obj);
                        }
                    }

                    List<ModInfo> currentMods = scanEnvironment();
                    List<ModInfo> violating = new ArrayList<>();

                    for (ModInfo mod : currentMods) {
                        // Always allow self/companion mod
                        if (mod.id.equals(VaultOPMod.MOD_ID) || mod.id.equals("vaultop_tournaments")) {
                            continue;
                        }

                        // Always allow basic fabric libraries unless strict mode requires otherwise
                        if (mod.id.startsWith("fabric-") || mod.id.equals("fabric")) {
                            continue;
                        }

                        if (!allowedMap.containsKey(mod.id)) {
                            violating.add(mod);
                        } else {
                            JsonObject allowedSpec = allowedMap.get(mod.id);
                            if (allowedSpec.has("hash")) {
                                String requiredHash = allowedSpec.get("hash").getAsString();
                                if (!requiredHash.isEmpty() && !requiredHash.equalsIgnoreCase(mod.hash)) {
                                    violating.add(mod);
                                }
                            }
                        }
                    }

                    boolean compliant = violating.isEmpty();

                    // Send result back to server
                    JsonObject logPayload = new JsonObject();
                    logPayload.addProperty("compliant", compliant);
                    JsonArray violatingJson = new JsonArray();
                    for (ModInfo v : violating) {
                        JsonObject vObj = new JsonObject();
                        vObj.addProperty("id", v.id);
                        vObj.addProperty("filename", v.filename);
                        vObj.addProperty("hash", v.hash);
                        violatingJson.add(vObj);
                    }
                    logPayload.add("violatingMods", violatingJson);

                    String token = VaultOPMod.getInstance().getSessionManager().getSessionToken();
                    VaultOPMod.getInstance().getRestClient().submitVerificationLog(tournamentId, token, logPayload);

                    return new VerificationResult(compliant, violating);
                });
    }
}

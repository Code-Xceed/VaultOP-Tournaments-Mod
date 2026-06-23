package com.vaultop.mod.discord;

import dev.firstdark.discordrpc.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import com.vaultop.mod.gui.*;
import com.vaultop.mod.VaultOPMod;

public class DiscordPresenceManager {
    private static final String CLIENT_ID = "1517400659505319976";
    private static DiscordRpc rpc;
    private static boolean initialized = false;
    private static long startTimestamp;

    private static String lastDetails = "";
    private static String lastState = "";

    public static void init() {
        if (initialized) return;
        try {
            rpc = new DiscordRpc();
            rpc.setDebugMode(false);

            RPCEventHandler handler = new RPCEventHandler() {
                @Override
                public void ready(User user) {
                    System.out.println("[VaultOP] Discord RPC ready: " + user.getUsername());
                    updatePresence(lastDetails.isEmpty() ? "Main Menu" : lastDetails, lastState.isEmpty() ? "Playing VaultOP tournaments" : lastState);
                }

                @Override
                public void disconnected(ErrorCode errorCode, String message) {
                    System.out.println("[VaultOP] Discord RPC disconnected: " + message);
                }
            };

            rpc.init(CLIENT_ID, handler, false);
            startTimestamp = System.currentTimeMillis() / 1000;
            initialized = true;

            System.out.println("[VaultOP] Discord Rich Presence initialized successfully.");
        } catch (Throwable t) {
            System.err.println("[VaultOP] Failed to initialize Discord Rich Presence: " + t.getMessage());
            initialized = false;
        }
    }

    public static void updatePresence(String details, String state) {
        if (!initialized || rpc == null) return;
        try {
            String websiteUrl = "https://vault-op-tournaments.vercel.app";
            try {
                if (VaultOPMod.getInstance() != null && VaultOPMod.getInstance().getConfigManager() != null) {
                    websiteUrl = VaultOPMod.getInstance().getConfigManager().getWebsiteUrl();
                }
            } catch (Throwable t) {
                // Fallback
            }

            DiscordRichPresence presence = DiscordRichPresence.builder()
                .details(details)
                .state(state)
                .largeImageKey("logo")
                .largeImageText("VaultOP Tournaments")
                .startTimestamp(startTimestamp)
                .activityType(ActivityType.PLAYING)
                .button(DiscordRichPresence.RPCButton.of("🌐 Website", websiteUrl))
                .button(DiscordRichPresence.RPCButton.of("💬 Discord", "https://discord.gg/E3A7Fv5RBm"))
                .build();

            rpc.updatePresence(presence);
        } catch (Throwable t) {
            System.err.println("[VaultOP] Failed to update Discord Rich Presence: " + t.getMessage());
        }
    }

    public static void tick(MinecraftClient client) {
        if (!initialized || rpc == null) return;

        String details = "Main Menu";
        String state = "Playing VaultOP tournaments";

        if (client.world != null && client.player != null) {
            if (client.getCurrentServerEntry() != null) {
                details = "Competing in Tournaments";
                state = "Playing on: " + client.getCurrentServerEntry().name;
            } else if (client.isIntegratedServerRunning()) {
                details = "Playing SinglePlayer";
            } else {
                details = "In Game";
            }
        } else {
            Screen currentScreen = client.currentScreen;
            if (currentScreen != null) {
                String className = currentScreen.getClass().getSimpleName();
                if (currentScreen instanceof AuthScreen) {
                    details = "Authenticating";
                } else if (currentScreen instanceof HomeScreen) {
                    details = "In Dashboard";
                } else if (currentScreen instanceof TournamentListScreen) {
                    details = "Browsing Tournaments";
                } else if (currentScreen instanceof TournamentDetailScreen) {
                    details = "Viewing Tournament details";
                } else if (currentScreen instanceof ProfileScreen) {
                    details = "Viewing Profile";
                } else {
                    if (className.equals("TitleScreen")) {
                        details = "Main Menu";
                    } else {
                        details = "In Menus";
                    }
                }
            }
        }

        if (!details.equals(lastDetails) || !state.equals(lastState)) {
            lastDetails = details;
            lastState = state;
            updatePresence(details, state);
        }
    }

    public static void shutdown() {
        if (!initialized || rpc == null) return;
        initialized = false;
        try {
            rpc.shutdown();
            System.out.println("[VaultOP] Discord Rich Presence shut down.");
        } catch (Throwable t) {
            // Ignore
        }
    }
}

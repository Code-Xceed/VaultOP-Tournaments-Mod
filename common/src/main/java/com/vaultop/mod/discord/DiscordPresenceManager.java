package com.vaultop.mod.discord;

import club.minnced.discord.rpc.DiscordEventHandlers;
import club.minnced.discord.rpc.DiscordRPC;
import club.minnced.discord.rpc.DiscordRichPresence;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import com.vaultop.mod.gui.*;

public class DiscordPresenceManager {
    private static final String CLIENT_ID = "1517400659505319976";
    private static boolean initialized = false;
    private static Thread callbackThread;
    private static long startTimestamp;

    private static String lastDetails = "";
    private static String lastState = "";

    public static void init() {
        if (initialized) return;
        try {
            DiscordRPC lib = DiscordRPC.INSTANCE;
            DiscordEventHandlers handlers = new DiscordEventHandlers();
            handlers.ready = (user) -> {
                System.out.println("Discord RPC ready: " + user.username);
            };
            
            lib.Discord_Initialize(CLIENT_ID, handlers, true, null);
            startTimestamp = System.currentTimeMillis() / 1000;
            
            DiscordRichPresence presence = new DiscordRichPresence();
            presence.state = "Playing VaultOP tournaments";
            presence.details = "Main Menu";
            presence.largeImageKey = "logo";
            presence.largeImageText = "VaultOP Tournaments";
            presence.startTimestamp = startTimestamp;
            
            lib.Discord_UpdatePresence(presence);
            initialized = true;

            // Start callback processing thread
            callbackThread = new Thread(() -> {
                while (!Thread.currentThread().isInterrupted() && initialized) {
                    try {
                        lib.Discord_RunCallbacks();
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        break;
                    } catch (Exception e) {
                        // Suppress background thread exceptions
                    }
                }
            }, "DiscordRPC-Callbacks");
            callbackThread.setDaemon(true);
            callbackThread.start();
            System.out.println("Discord Rich Presence initialized successfully.");
        } catch (Throwable t) {
            System.err.println("Failed to initialize Discord Rich Presence: " + t.getMessage());
            initialized = false;
        }
    }

    public static void updatePresence(String details, String state) {
        if (!initialized) return;
        try {
            DiscordRichPresence presence = new DiscordRichPresence();
            presence.details = details;
            presence.state = state;
            presence.largeImageKey = "logo";
            presence.largeImageText = "VaultOP Tournaments";
            presence.startTimestamp = startTimestamp;
            DiscordRPC.INSTANCE.Discord_UpdatePresence(presence);
        } catch (Throwable t) {
            // Suppress updates if failed
        }
    }

    public static void tick(MinecraftClient client) {
        if (!initialized) return;

        String details = "Main Menu";
        String state = "Playing VaultOP tournaments";

        if (client.world != null && client.player != null) {
            if (client.getCurrentServerEntry() != null) {
                details = "Playing on: " + client.getCurrentServerEntry().name;
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
        if (!initialized) return;
        initialized = false;
        if (callbackThread != null) {
            callbackThread.interrupt();
        }
        try {
            DiscordRPC.INSTANCE.Discord_Shutdown();
            System.out.println("Discord Rich Presence shut down.");
        } catch (Throwable t) {
            // Suppress shutdown exception
        }
    }
}

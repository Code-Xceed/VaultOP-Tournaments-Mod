package com.vaultop.mod.discord;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import com.vaultop.mod.gui.*;
import com.vaultop.mod.VaultOPMod;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingQueue;

public class DiscordPresenceManager {
    private static final String CLIENT_ID = "1518987034604077116";
    private static boolean initialized = false;
    private static Thread workerThread;
    private static final LinkedBlockingQueue<PresenceUpdate> queue = new LinkedBlockingQueue<>();
    private static long startTimestamp;

    private static String lastDetails = "";
    private static String lastState = "";

    private static class PresenceUpdate {
        final String details;
        final String state;

        PresenceUpdate(String details, String state) {
            this.details = details;
            this.state = state;
        }
    }

    public static void init() {
        if (initialized) return;
        startTimestamp = System.currentTimeMillis() / 1000;
        initialized = true;

        workerThread = new Thread(() -> {
            java.io.Closeable pipeStream = null;
            java.io.DataOutputStream out = null;
            java.io.DataInputStream in = null;
            boolean connected = false;

            while (initialized) {
                if (!connected) {
                    try {
                        for (int i = 0; i < 10; i++) {
                            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                                File pipe = new File("\\\\.\\pipe\\discord-ipc-" + i);
                                if (pipe.exists()) {
                                    java.io.RandomAccessFile raf = new java.io.RandomAccessFile(pipe, "rw");
                                    pipeStream = raf;
                                    out = new java.io.DataOutputStream(new java.io.OutputStream() {
                                        @Override
                                        public void write(int b) throws IOException {
                                            raf.write(b);
                                        }
                                        @Override
                                        public void write(byte[] b, int off, int len) throws IOException {
                                            raf.write(b, off, len);
                                        }
                                    });
                                    in = new java.io.DataInputStream(new java.io.InputStream() {
                                        @Override
                                        public int read() throws IOException {
                                            return raf.read();
                                        }
                                        @Override
                                        public int read(byte[] b, int off, int len) throws IOException {
                                            return raf.read(b, off, len);
                                        }
                                    });
                                    connected = true;
                                    break;
                                }
                            } else {
                                String[] paths = {
                                    System.getenv("XDG_RUNTIME_DIR"),
                                    System.getenv("TMPDIR"),
                                    System.getenv("TMP"),
                                    System.getenv("TEMP"),
                                    "/tmp"
                                };
                                for (String baseDir : paths) {
                                    if (baseDir == null || baseDir.isEmpty()) continue;
                                    File socketFile = new File(baseDir, "discord-ipc-" + i);
                                    if (socketFile.exists()) {
                                        java.net.SocketAddress address = java.net.UnixDomainSocketAddress.of(socketFile.getPath());
                                        java.nio.channels.SocketChannel channel = java.nio.channels.SocketChannel.open(java.net.StandardProtocolFamily.UNIX);
                                        channel.connect(address);
                                        pipeStream = channel;
                                        out = new java.io.DataOutputStream(java.nio.channels.Channels.newOutputStream(channel));
                                        in = new java.io.DataInputStream(java.nio.channels.Channels.newInputStream(channel));
                                        connected = true;
                                        break;
                                    }
                                }
                                if (connected) break;
                            }
                        }

                        if (connected) {
                            String handshakePayload = "{\"v\":1,\"client_id\":\"" + CLIENT_ID + "\"}";
                            writePacket(out, 0, handshakePayload);
                            readPacket(in);

                            sendPresenceUpdate(out, lastDetails.isEmpty() ? "Main Menu" : lastDetails, lastState.isEmpty() ? "Playing VaultOP tournaments" : lastState);
                            System.out.println("[VaultOP] Discord Rich Presence connected.");
                        }
                    } catch (Throwable t) {
                        connected = false;
                        if (pipeStream != null) {
                            try { pipeStream.close(); } catch (Throwable ignore) {}
                            pipeStream = null;
                        }
                    }
                }

                if (!connected) {
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        break;
                    }
                    continue;
                }

                try {
                    PresenceUpdate update = queue.take();
                    sendPresenceUpdate(out, update.details, update.state);
                } catch (InterruptedException e) {
                    break;
                } catch (Throwable t) {
                    connected = false;
                    if (pipeStream != null) {
                        try { pipeStream.close(); } catch (Throwable ignore) {}
                        pipeStream = null;
                    }
                }
            }

            if (pipeStream != null) {
                try { pipeStream.close(); } catch (Throwable ignore) {}
            }
        }, "VaultOP-Discord-RPC");

        workerThread.setDaemon(true);
        workerThread.start();
    }

    private static void sendPresenceUpdate(java.io.DataOutputStream out, String details, String state) throws IOException {
        String websiteUrl = "https://vault-op-tournaments.vercel.app";
        try {
            if (VaultOPMod.getInstance() != null && VaultOPMod.getInstance().getConfigManager() != null) {
                websiteUrl = VaultOPMod.getInstance().getConfigManager().getWebsiteUrl();
            }
        } catch (Throwable ignore) {}

        String payload = "{"
            + "\"cmd\":\"SET_ACTIVITY\","
            + "\"args\":{"
                + "\"pid\":" + ProcessHandle.current().pid() + ","
                + "\"activity\":{"
                    + "\"details\":\"" + escapeJson(details) + "\","
                    + "\"state\":\"" + escapeJson(state) + "\","
                    + "\"timestamps\":{"
                        + "\"start\":" + startTimestamp
                    + "},"
                    + "\"assets\":{"
                        + "\"large_image\":\"logo\","
                        + "\"large_text\":\"VaultOP Tournaments\""
                    + "},"
                    + "\"buttons\":["
                        + "{\"label\":\"\uD83C\uDF10 Website\",\"url\":\"" + escapeJson(websiteUrl) + "\"},"
                        + "{\"label\":\"\uD83D\uDCAC Discord\",\"url\":\"https://discord.gg/E3A7Fv5RBm\"}"
                    + "]"
                + "}"
            + "},"
            + "\"nonce\":\"" + System.nanoTime() + "\""
            + "}";

        writePacket(out, 1, payload);
    }

    private static void writePacket(java.io.DataOutputStream out, int op, String payload) throws IOException {
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        out.write(op & 0xFF);
        out.write((op >> 8) & 0xFF);
        out.write((op >> 16) & 0xFF);
        out.write((op >> 24) & 0xFF);
        out.write(bytes.length & 0xFF);
        out.write((bytes.length >> 8) & 0xFF);
        out.write((bytes.length >> 16) & 0xFF);
        out.write((bytes.length >> 24) & 0xFF);
        out.write(bytes);
        out.flush();
    }

    private static void readPacket(java.io.DataInputStream in) throws IOException {
        int b1 = in.readUnsignedByte();
        int b2 = in.readUnsignedByte();
        int b3 = in.readUnsignedByte();
        int b4 = in.readUnsignedByte();

        int l1 = in.readUnsignedByte();
        int l2 = in.readUnsignedByte();
        int l3 = in.readUnsignedByte();
        int l4 = in.readUnsignedByte();
        int len = l1 | (l2 << 8) | (l3 << 16) | (l4 << 24);

        byte[] bytes = new byte[len];
        in.readFully(bytes);
    }

    private static String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\b", "\\b")
                  .replace("\f", "\\f")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    public static void updatePresence(String details, String state) {
        if (!initialized) return;
        queue.offer(new PresenceUpdate(details, state));
    }

    public static void tick(MinecraftClient client) {
        if (!initialized) return;

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
        if (!initialized) return;
        initialized = false;
        if (workerThread != null) {
            workerThread.interrupt();
        }
    }
}
